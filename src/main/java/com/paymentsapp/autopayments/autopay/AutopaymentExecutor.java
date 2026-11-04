package com.paymentsapp.autopayments.autopay;

import com.paymentsapp.autopayments.autopay.Autopayment.Run;
import com.paymentsapp.autopayments.client.Clients;
import com.paymentsapp.autopayments.client.Clients.PaymentResult;
import com.paymentsapp.autopayments.web.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

// Проведение автоплатежа за дату: котировка в pricing → платёж в payment с основного счёта,
// не хватило денег — вся сумма с запасного. Сервис недоступен — запуск остаётся PENDING и повторится на следующем проходе
// с тем же ключом идемпотентности и той же котировкой.
@Component
public class AutopaymentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutopaymentExecutor.class);
    private static final Set<String> FINAL = Set.of("SUCCESS", "FAILED", "PROCESSING");

    private final AutopaymentRepository repo;
    private final Clients clients;

    public AutopaymentExecutor(AutopaymentRepository repo, Clients clients) {
        this.repo = repo;
        this.clients = clients;
    }

    public void run(UUID autopaymentId, LocalDate today) {
        Autopayment a = repo.get(autopaymentId).orElse(null);
        if (a == null || !"ACTIVE".equals(a.status()) || a.nextRunDate().isAfter(today)) {
            return;
        }
        Run run = repo.claim(a, a.nextRunDate());
        if (FINAL.contains(run.status())) {
            return;
        }
        List<UUID> accounts = new ArrayList<>(List.of(a.sourceAccountId()));
        if (a.backupAccountId() != null) {
            accounts.add(a.backupAccountId());
        }
        // Уже пробовали запасной — продолжаем с него
        int start = run.accountId() != null && run.accountId().equals(a.backupAccountId()) ? 1 : 0;
        LocalDate next = a.schedule().after(run.runDate());
        try {
            for (int i = start; i < accounts.size(); i++) {
                UUID account = accounts.get(i);
                String token;
                try {
                    token = repo.quoteToken(run.id(), account).orElse(null);
                    if (token == null) {
                        token = clients.quote(a.userId(), account, a.provider().code(), a.amount()).quoteToken();
                        repo.attempt(run.id(), account, token);
                    }
                } catch (ApiException e) {
                    repo.finish(a, run, "FAILED", null, null, e.getMessage(), next);
                    return;
                }
                PaymentResult r = clients.pay(a.userId(), "auto:" + run.id() + ":" + account, account, a.provider().code(),
                        a.fields(), a.amount(), token);
                boolean lastAccount = i == accounts.size() - 1;
                if ("SUCCESS".equals(r.status())) {
                    repo.finish(a, run, "SUCCESS", r.paymentId(), r.commission(), i == 0 ? "Оплачено" : "Оплачено с запасного счёта", next);
                    return;
                }
                if ("FAILED".equals(r.status()) && "INSUFFICIENT_FUNDS".equals(r.failureCode()) && !lastAccount) {
                    log.info("автоплатёж {}: на основном счёте не хватило денег, пробуем запасной", a.id());
                    continue;
                }
                if ("FAILED".equals(r.status()) || "REJECTED".equals(r.status())) {
                    repo.finish(a, run, "FAILED", r.paymentId(), null, r.message(), next);
                    return;
                }
                // Платёж ещё проводится — его доведёт payment, следующую дату ставим как обычно
                repo.finish(a, run, "PROCESSING", r.paymentId(), r.commission(), "Платёж обрабатывается", next);
                return;
            }
        } catch (RestClientException e) {
            log.warn("автоплатёж {}: сервис недоступен, повторим позже: {}", a.id(), e.getMessage());
        }
    }
}
