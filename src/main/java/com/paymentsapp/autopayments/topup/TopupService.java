package com.paymentsapp.autopayments.topup;

import java.time.Clock;
import com.paymentsapp.autopayments.autopay.Schedule;
import com.paymentsapp.autopayments.client.Clients;
import com.paymentsapp.autopayments.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class TopupService {

    private static final BigDecimal MIN = BigDecimal.TEN;

    private final JdbcClient jdbc;
    private final Clients clients;
    private final TopupRepository repo;
    private final Clock clock;

    public TopupService(JdbcClient jdbc, Clients clients, TopupRepository repo, Clock clock) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.repo = repo;
        this.clock = clock;
    }

    public List<Topup> list(UUID userId) {
        return repo.list(userId);
    }

    public Topup.WithRuns get(UUID userId, UUID id) {
        Topup t = repo.get(userId, id);
        return new Topup.WithRuns(t, repo.runs(id));
    }

    public Topup create(UUID userId, Request req) {
        List<Clients.Account> own = clients.accounts(userId);
        Clients.Account destination = own.stream().filter(a -> a.id().equals(req.destinationAccountId()) && a.usable())
                .findFirst().orElseThrow(() -> ApiException.badRequest("Выберите, куда переводить: кошелёк или карту нашего банка"));

        // Откуда: свой кошелёк / карта или свой счёт в другом банке по СБП
        Clients.Account source;
        String bankId = null;
        if ("SBP".equals(req.sourceKind())) {
            if (req.sourceBankId() == null || req.sourceBankName() == null) {
                throw ApiException.badRequest("Выберите банк, из которого пополнять");
            }
            source = clients.sbpSource(userId, req.sourceBankId(), req.sourceBankName());
            bankId = req.sourceBankId();
        } else {
            source = own.stream().filter(a -> a.id().equals(req.sourceAccountId()) && a.usable()).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Выберите, откуда списать"));
        }
        if (source.id().equals(destination.id())) {
            throw ApiException.badRequest("Счёт списания и счёт зачисления должны отличаться");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate nextRun = null;
        switch (req.kind() == null ? "" : req.kind()) {
            case "MONTHLY" -> {
                atLeast(req.amount(), "Сумма пополнения — от 10 ₽");
                nextRun = Schedule.of("MONTHLY", req.dayOfMonth(), null, null, today).nextOnOrAfter(today);
            }
            case "BY_BALANCE" -> {
                atLeast(req.amount(), "Сумма пополнения — от 10 ₽");
                if (req.threshold() == null || req.threshold().signum() < 0) {
                    throw ApiException.badRequest("Укажите баланс, при котором пополнять");
                }
            }
            case "CONSTANT_BALANCE" -> atLeast(req.targetBalance(), "Баланс, который поддерживать, — от 10 ₽");
            default -> throw ApiException.badRequest("Выберите тип автопополнения");
        }
        if (req.monthlyLimit() != null) {
            atLeast(req.monthlyLimit(), "Лимит в месяц — от 10 ₽");
            if (req.amount() != null && !"CONSTANT_BALANCE".equals(req.kind()) && req.monthlyLimit().compareTo(req.amount()) < 0) {
                throw ApiException.badRequest("Лимит в месяц не может быть меньше суммы пополнения");
            }
        }
        // Есть ли тариф на такое пополнение (например, с карты другого банка — свой)
        BigDecimal probe = req.amount() != null ? req.amount() : req.targetBalance();
        clients.quoteOwn(userId, source.id(), destination.id(), probe);

        String name = req.name() == null || req.name().isBlank() ? destination.displayTitle() : req.name().trim();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into topups (id, user_id, name, source_kind, source_account_id, source_title, source_bank_id,
                            destination_account_id, destination_title, kind, day_of_month, amount, threshold, target_balance,
                            monthly_limit, next_run_date, next_check_at)
                        values (:id, :user, :name, :skind, :source, :stitle, :bank, :dest, :dtitle, :kind, :day, :amount,
                            :threshold, :target, :limit, :next, :check)
                        """)
                .param("id", id).param("user", userId).param("name", name.length() > 60 ? name.substring(0, 60) : name)
                .param("skind", "SBP".equals(req.sourceKind()) ? "SBP" : "OWN").param("source", source.id())
                .param("stitle", source.displayTitle()).param("bank", bankId).param("dest", destination.id())
                .param("dtitle", destination.displayTitle()).param("kind", req.kind())
                .param("day", "MONTHLY".equals(req.kind()) ? req.dayOfMonth() : null)
                .param("amount", "CONSTANT_BALANCE".equals(req.kind()) ? null : req.amount())
                .param("threshold", "BY_BALANCE".equals(req.kind()) ? req.threshold() : null)
                .param("target", "CONSTANT_BALANCE".equals(req.kind()) ? req.targetBalance() : null)
                .param("limit", "MONTHLY".equals(req.kind()) ? null : req.monthlyLimit())
                .param("next", nextRun).param("check", "MONTHLY".equals(req.kind()) ? null : OffsetDateTime.now())
                .update();
        return repo.get(userId, id);
    }

    public Topup setStatus(UUID userId, UUID id, String status) {
        Topup t = repo.get(userId, id);
        if (!"ACTIVE".equals(status) && !"PAUSED".equals(status)) {
            throw ApiException.badRequest("Статус — ACTIVE или PAUSED");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate next = t.nextRunDate();
        if ("ACTIVE".equals(status) && "MONTHLY".equals(t.kind()) && next.isBefore(today)) {
            next = Schedule.of("MONTHLY", t.dayOfMonth(), null, null, today).nextOnOrAfter(today);
        }
        jdbc.sql("update topups set status = :status, next_run_date = :next, next_check_at = :check, updated_at = now() where id = :id")
                .param("id", id).param("status", status).param("next", next)
                .param("check", "MONTHLY".equals(t.kind()) ? null : OffsetDateTime.now()).update();
        return repo.get(userId, id);
    }

    public void delete(UUID userId, UUID id) {
        repo.get(userId, id);
        jdbc.sql("delete from topups where id = :id").param("id", id).update();
    }

    private static void atLeast(BigDecimal v, String message) {
        if (v == null || v.compareTo(MIN) < 0) {
            throw ApiException.badRequest(message);
        }
    }

    public record Request(String name, String sourceKind, UUID sourceAccountId, String sourceBankId, String sourceBankName,
                          UUID destinationAccountId, String kind, Integer dayOfMonth, BigDecimal amount, BigDecimal threshold,
                          BigDecimal targetBalance, BigDecimal monthlyLimit) {
    }
}
