package com.paymentsapp.autopayments.topup;

import com.paymentsapp.autopayments.autopay.Schedule;
import com.paymentsapp.autopayments.client.Account;
import com.paymentsapp.autopayments.client.AccountClient;
import com.paymentsapp.autopayments.client.PaymentClient;
import com.paymentsapp.autopayments.client.PaymentOutcome;
import com.paymentsapp.autopayments.client.PricingClient;
import com.paymentsapp.autopayments.common.RunMessage;
import com.paymentsapp.autopayments.common.RunStatus;
import com.paymentsapp.autopayments.common.Status;
import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.autopayments.topup.Topup.Run;
import com.paymentsapp.autopayments.topup.TopupPlan.Decision;
import com.paymentsapp.platform.error.DomainException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

// Проведение автопополнения: раз в месяц — по дате; по балансу и «постоянный баланс» — смотрим баланс счёта зачисления
// в account и, если он опустился, переводим между своими счетами (OWN_TRANSFER) в пределах лимита месяца.
@Component
public class TopupExecutor {

    private static final Logger log = LoggerFactory.getLogger(TopupExecutor.class);
    // Ключ запуска: m:<дата> — раз в месяц, b:<момент> — по балансу; ключ идемпотентности платежа — topup:<запуск>
    private static final String MONTHLY_TRIGGER = "m:";
    private static final String BALANCE_TRIGGER = "b:";
    private static final String KEY_PREFIX = "topup:";

    private final TopupRepository repo;
    private final AccountClient accounts;
    private final PricingClient pricing;
    private final PaymentClient payments;
    private final AutopayProperties props;

    public TopupExecutor(TopupRepository repo, AccountClient accounts, PricingClient pricing, PaymentClient payments,
                         AutopayProperties props) {
        this.repo = repo;
        this.accounts = accounts;
        this.pricing = pricing;
        this.payments = payments;
        this.props = props;
    }

    public void run(UUID topupId, LocalDate today, OffsetDateTime now) {
        Topup t = repo.get(topupId).orElse(null);
        if (t == null || t.status() != Status.ACTIVE) {
            return;
        }
        try {
            if (t.kind() == TopupKind.MONTHLY) {
                runMonthly(t, today);
            } else {
                runByBalance(t, now);
            }
        } catch (RestClientException e) {
            log.warn("автопополнение {}: сервис недоступен, повторим позже: {}", t.id(), e.getMessage());
        }
    }

    private void runMonthly(Topup t, LocalDate today) {
        if (t.nextRunDate().isAfter(today)) {
            return;
        }
        Run run = repo.claim(t.id(), MONTHLY_TRIGGER + t.nextRunDate(), t.amount(), null);
        LocalDate next = Schedule.monthly(t.dayOfMonth(), today).after(t.nextRunDate());
        if (run.status() != RunStatus.PENDING) {
            repo.finish(t, run, run.status(), run.paymentId(), run.commission(), run.message(), next, null);
            return;
        }
        execute(t, run, next, null);
    }

    // Сначала доводим незавершённый запуск, потом смотрим баланс
    private void runByBalance(Topup t, OffsetDateTime now) {
        Run pending = repo.pending(t.id()).orElse(null);
        if (pending != null) {
            execute(t, pending, null, now);
            return;
        }
        Account destination = accounts.accounts(t.userId()).stream()
                .filter(a -> a.id().equals(t.destinationAccountId())).findFirst().orElse(null);
        if (destination == null || destination.available() == null) {
            repo.postponeCheck(t.id(), now.plus(props.topup().afterFailure()));
            return;
        }
        Decision decision = TopupPlan.decide(t, destination.available(), props.minAmount());
        switch (decision.action()) {
            case CHECK_NEXT_TICK -> repo.postponeCheck(t.id(), now);
            case LIMIT_REACHED -> repo.postponeCheck(t.id(), now.plus(props.topup().afterFailure()));
            case TOP_UP -> execute(t, repo.claim(t.id(), BALANCE_TRIGGER + now.toInstant().toEpochMilli(),
                    decision.amount(), destination.available()), null, now);
        }
    }

    private void execute(Topup t, Run run, LocalDate nextRunDate, OffsetDateTime now) {
        String token = repo.quoteToken(run.id()).orElse(null);
        if (token == null) {
            try {
                token = pricing.ownTransferQuote(t.userId(), t.sourceAccountId(), t.destinationAccountId(), run.amount())
                        .quoteToken();
            } catch (DomainException e) {
                repo.finish(t, run, RunStatus.FAILED, null, null, e.getMessage(), nextRunDate,
                        nextCheck(now, props.topup().afterFailure()));
                return;
            }
            repo.saveQuote(run.id(), token);
        }
        PaymentOutcome r = payments.payOwn(t.userId(), KEY_PREFIX + run.id(), t.sourceAccountId(), t.destinationAccountId(),
                run.amount(), token);
        RunStatus status = switch (r.result()) {
            case SUCCESS -> RunStatus.SUCCESS;
            case FAILED, REJECTED -> RunStatus.FAILED;
            case IN_PROGRESS -> RunStatus.PROCESSING;
        };
        String message = switch (status) {
            case SUCCESS -> RunMessage.TOPPED_UP.text();
            case PROCESSING -> RunMessage.TRANSFER_PROCESSING.text();
            default -> r.message();
        };
        Duration pause = status == RunStatus.FAILED ? props.topup().afterFailure() : props.topup().afterSuccess();
        repo.finish(t, run, status, r.paymentId(), r.commission(), message, nextRunDate, nextCheck(now, pause));
    }

    // Раз в месяц время проверки не нужно (null), по балансу — через паузу
    private static OffsetDateTime nextCheck(OffsetDateTime now, Duration pause) {
        return now == null ? null : now.plus(pause);
    }
}
