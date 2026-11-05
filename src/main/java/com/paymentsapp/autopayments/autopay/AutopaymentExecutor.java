package com.paymentsapp.autopayments.autopay;

import com.paymentsapp.autopayments.autopay.Autopayment.Run;
import com.paymentsapp.autopayments.client.PaymentClient;
import com.paymentsapp.autopayments.client.PaymentOutcome;
import com.paymentsapp.autopayments.client.PaymentOutcome.Result;
import com.paymentsapp.autopayments.client.PricingClient;
import com.paymentsapp.autopayments.common.RunMessage;
import com.paymentsapp.autopayments.common.RunStatus;
import com.paymentsapp.autopayments.common.Status;
import com.paymentsapp.platform.error.DomainException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

// Проведение автоплатежа за дату: котировка в pricing → платёж в payment с основного счёта,
// не хватило денег — вся сумма с запасного. Сервис недоступен — запуск остаётся PENDING и повторится
// с тем же ключом идемпотентности и той же котировкой.
@Component
public class AutopaymentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutopaymentExecutor.class);
    // Ключ идемпотентности платежа: auto:<запуск>:<счёт>
    private static final String KEY_PREFIX = "auto:";
    private static final String KEY_SEPARATOR = ":";

    private final AutopaymentRepository repo;
    private final PricingClient pricing;
    private final PaymentClient payments;

    public AutopaymentExecutor(AutopaymentRepository repo, PricingClient pricing, PaymentClient payments) {
        this.repo = repo;
        this.pricing = pricing;
        this.payments = payments;
    }

    public void run(UUID autopaymentId, LocalDate today) {
        Autopayment a = repo.get(autopaymentId).orElse(null);
        if (a == null || a.status() != Status.ACTIVE || a.nextRunDate().isAfter(today)) {
            return;
        }
        Run run = repo.claim(a, a.nextRunDate());
        if (run.status() != RunStatus.PENDING) {
            return;
        }
        List<UUID> accounts = new ArrayList<>(List.of(a.sourceAccountId()));
        if (a.backupAccountId() != null) {
            accounts.add(a.backupAccountId());
        }
        // Уже пробовали запасной — продолжаем с него
        int start = a.backupAccountId() != null && a.backupAccountId().equals(run.accountId()) ? 1 : 0;
        LocalDate next = a.schedule().after(run.runDate());
        try {
            for (int i = start; i < accounts.size(); i++) {
                UUID account = accounts.get(i);
                String token;
                try {
                    token = repo.quoteToken(run.id(), account).orElse(null);
                    if (token == null) {
                        token = pricing.billQuote(a.userId(), account, a.provider().code(), a.amount()).quoteToken();
                        repo.attempt(run.id(), account, token);
                    }
                } catch (DomainException e) {
                    repo.finish(a, run, RunStatus.FAILED, null, null, e.getMessage(), next);
                    return;
                }
                PaymentOutcome r = payments.payBill(a.userId(), KEY_PREFIX + run.id() + KEY_SEPARATOR + account, account,
                        a.provider().code(), a.fields(), a.amount(), token);
                boolean lastAccount = i == accounts.size() - 1;
                if (r.result() == Result.SUCCESS) {
                    RunMessage message = i == 0 ? RunMessage.PAID : RunMessage.PAID_FROM_BACKUP;
                    repo.finish(a, run, RunStatus.SUCCESS, r.paymentId(), r.commission(), message.text(), next);
                    return;
                }
                if (r.insufficientFunds() && !lastAccount) {
                    log.info("автоплатёж {}: на основном счёте не хватило денег, пробуем запасной", a.id());
                    continue;
                }
                if (r.result() == Result.FAILED || r.result() == Result.REJECTED) {
                    repo.finish(a, run, RunStatus.FAILED, r.paymentId(), null, r.message(), next);
                    return;
                }
                // Платёж ещё проводится — его доведёт payment, следующую дату ставим как обычно
                repo.finish(a, run, RunStatus.PROCESSING, r.paymentId(), r.commission(),
                        RunMessage.PAYMENT_PROCESSING.text(), next);
                return;
            }
        } catch (RestClientException e) {
            log.warn("автоплатёж {}: сервис недоступен, повторим позже: {}", a.id(), e.getMessage());
        }
    }
}
