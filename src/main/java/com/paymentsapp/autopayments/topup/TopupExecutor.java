package com.paymentsapp.autopayments.topup;

import com.paymentsapp.autopayments.autopay.Schedule;
import com.paymentsapp.autopayments.client.Clients;
import com.paymentsapp.autopayments.client.Clients.PaymentResult;
import com.paymentsapp.autopayments.topup.Topup.Run;
import com.paymentsapp.autopayments.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

// Проведение автопополнения: раз в месяц — по дате; по балансу и «постоянный баланс» — смотрим баланс счёта зачисления
// в account и, если он опустился, переводим между своими счетами (OWN_TRANSFER) в пределах лимита месяца.
@Component
public class TopupExecutor {

    private static final Logger log = LoggerFactory.getLogger(TopupExecutor.class);
    private static final BigDecimal MIN = BigDecimal.TEN;
    // После пополнения даём балансу обновиться, после отказа — не долбим источник каждые полминуты
    private static final Duration AFTER_SUCCESS = Duration.ofMinutes(1);
    private static final Duration AFTER_FAILURE = Duration.ofMinutes(30);

    private final TopupRepository repo;
    private final Clients clients;

    public TopupExecutor(TopupRepository repo, Clients clients) {
        this.repo = repo;
        this.clients = clients;
    }

    public void run(java.util.UUID topupId, LocalDate today, OffsetDateTime now) {
        Topup t = repo.get(topupId).orElse(null);
        if (t == null || !"ACTIVE".equals(t.status())) {
            return;
        }
        try {
            if ("MONTHLY".equals(t.kind())) {
                if (t.nextRunDate().isAfter(today)) {
                    return;
                }
                Run run = repo.claim(t.id(), "m:" + t.nextRunDate(), t.amount(), null);
                LocalDate next = new Schedule("MONTHLY", t.dayOfMonth(), null, today).after(t.nextRunDate());
                if (!"PENDING".equals(run.status())) {
                    repo.finish(t, run, run.status(), run.paymentId(), run.commission(), run.message(), next, null);
                    return;
                }
                execute(t, run, next, null);
                return;
            }
            // По балансу: сначала доводим незавершённый запуск, потом смотрим баланс
            Run pending = repo.pending(t.id()).orElse(null);
            if (pending != null) {
                execute(t, pending, null, now);
                return;
            }
            Clients.Account dest = clients.accounts(t.userId()).stream().filter(a -> a.id().equals(t.destinationAccountId()))
                    .findFirst().orElse(null);
            if (dest == null || dest.available() == null) {
                repo.postponeCheck(t.id(), now.plus(AFTER_FAILURE));
                return;
            }
            BigDecimal balance = dest.available();
            BigDecimal amount = null;
            if ("BY_BALANCE".equals(t.kind()) && balance.compareTo(t.threshold()) <= 0) {
                amount = t.amount();
            } else if ("CONSTANT_BALANCE".equals(t.kind()) && balance.compareTo(t.targetBalance()) < 0) {
                amount = t.targetBalance().subtract(balance).setScale(2, RoundingMode.HALF_UP);
            }
            if (amount == null) {
                repo.postponeCheck(t.id(), now);
                return;
            }
            // Лимит месяца: по балансу — не пополняем, если сумма не влезает; постоянный баланс — пополняем на остаток
            if (t.monthlyLimit() != null) {
                BigDecimal left = t.monthlyLimit().subtract(t.monthUsed());
                if ("CONSTANT_BALANCE".equals(t.kind()) && amount.compareTo(left) > 0) {
                    amount = left;
                }
                if (amount.compareTo(left) > 0 || left.compareTo(MIN) < 0) {
                    repo.postponeCheck(t.id(), now.plus(AFTER_FAILURE));
                    return;
                }
            }
            if (amount.compareTo(MIN) < 0) {
                repo.postponeCheck(t.id(), now);
                return;
            }
            Run run = repo.claim(t.id(), "b:" + now.toInstant().toEpochMilli(), amount, balance);
            execute(t, run, null, now);
        } catch (RestClientException e) {
            log.warn("автопополнение {}: сервис недоступен, повторим позже: {}", t.id(), e.getMessage());
        }
    }

    private void execute(Topup t, Run run, LocalDate nextRunDate, OffsetDateTime now) {
        String token = repo.quoteToken(run.id()).orElse(null);
        if (token == null) {
            try {
                token = clients.quoteOwn(t.userId(), t.sourceAccountId(), t.destinationAccountId(), run.amount()).quoteToken();
            } catch (ApiException e) {
                repo.finish(t, run, "FAILED", null, null, e.getMessage(), nextRunDate, now == null ? null : now.plus(AFTER_FAILURE));
                return;
            }
            repo.saveQuote(run.id(), token);
        }
        PaymentResult r = clients.payOwn(t.userId(), "topup:" + run.id(), t.sourceAccountId(), t.destinationAccountId(),
                run.amount(), token);
        String status = "SUCCESS".equals(r.status()) ? "SUCCESS"
                : "FAILED".equals(r.status()) || "REJECTED".equals(r.status()) ? "FAILED" : "PROCESSING";
        String message = "SUCCESS".equals(status) ? "Пополнено" : "PROCESSING".equals(status) ? "Перевод обрабатывается" : r.message();
        OffsetDateTime nextCheck = now == null ? null : now.plus("FAILED".equals(status) ? AFTER_FAILURE : AFTER_SUCCESS);
        repo.finish(t, run, status, r.paymentId(), r.commission(), message, nextRunDate, nextCheck);
    }
}
