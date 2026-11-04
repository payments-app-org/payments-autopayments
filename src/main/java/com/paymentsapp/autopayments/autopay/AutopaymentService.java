package com.paymentsapp.autopayments.autopay;

import com.paymentsapp.autopayments.template.Target;
import com.paymentsapp.autopayments.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AutopaymentService {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Target target;
    private final AutopaymentRepository repo;
    private final ZoneId zone;

    public AutopaymentService(JdbcClient jdbc, JsonMapper json, Target target, AutopaymentRepository repo,
                              @Value("${autopay.zone}") String zone) {
        this.jdbc = jdbc;
        this.json = json;
        this.target = target;
        this.repo = repo;
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public List<Autopayment> list(UUID userId) {
        return repo.list(userId);
    }

    public Autopayment.WithRuns get(UUID userId, UUID id) {
        Autopayment a = repo.get(userId, id);
        return new Autopayment.WithRuns(a, repo.runs(a.id()));
    }

    public Autopayment create(UUID userId, Request req) {
        if (req.backupAccountId() != null && req.backupAccountId().equals(req.sourceAccountId())) {
            throw ApiException.badRequest("Запасной счёт должен отличаться от основного");
        }
        List<UUID> accounts = new ArrayList<>();
        accounts.add(req.sourceAccountId());
        if (req.backupAccountId() != null) {
            accounts.add(req.backupAccountId());
        }
        Target.Checked t = target.check(userId, req.providerCode(), req.fields(), req.amount(), accounts);
        LocalDate today = today();
        Schedule s = Schedule.of(req.scheduleKind(), req.dayOfMonth(), req.periodDays(), req.startDate(), today);
        String name = req.name() == null || req.name().isBlank()
                ? (t.provider().name() + (t.target() != null ? " · " + t.target() : ""))
                : req.name().trim();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into autopayments (id, user_id, name, provider_code, provider_group, provider_name, provider_domain,
                            provider_color, provider_country, fields, target, amount, source_account_id, backup_account_id,
                            schedule_kind, day_of_month, period_days, start_date, next_run_date)
                        values (:id, :user, :name, :code, :group, :pname, :domain, :color, :country, cast(:fields as jsonb),
                            :target, :amount, :source, :backup, :kind, :day, :period, :start, :next)
                        """)
                .param("id", id).param("user", userId).param("name", name.length() > 60 ? name.substring(0, 60) : name)
                .param("code", t.provider().code()).param("group", t.provider().groupCode()).param("pname", t.provider().name())
                .param("domain", t.provider().domain()).param("color", t.provider().color()).param("country", t.provider().countryCode())
                .param("fields", json.writeValueAsString(t.fields())).param("target", t.target())
                .param("amount", req.amount()).param("source", req.sourceAccountId()).param("backup", req.backupAccountId())
                .param("kind", s.kind()).param("day", s.dayOfMonth()).param("period", s.periodDays())
                .param("start", s.startDate()).param("next", s.nextOnOrAfter(today))
                .update();
        return repo.get(userId, id);
    }

    // Приостановить / возобновить. После паузы платим со следующей даты по расписанию, пропущенные не догоняем.
    public Autopayment setStatus(UUID userId, UUID id, String status) {
        Autopayment a = repo.get(userId, id);
        if (!"ACTIVE".equals(status) && !"PAUSED".equals(status)) {
            throw ApiException.badRequest("Статус — ACTIVE или PAUSED");
        }
        LocalDate next = a.nextRunDate();
        if ("ACTIVE".equals(status) && next.isBefore(today())) {
            next = a.schedule().nextOnOrAfter(today());
        }
        jdbc.sql("update autopayments set status = :status, next_run_date = :next, updated_at = now() where id = :id")
                .param("id", id).param("status", status).param("next", next).update();
        return repo.get(userId, id);
    }

    public void delete(UUID userId, UUID id) {
        repo.get(userId, id);
        jdbc.sql("delete from autopayments where id = :id").param("id", id).update();
    }

    public record Request(String name, String providerCode, Map<String, String> fields, BigDecimal amount,
                          UUID sourceAccountId, UUID backupAccountId, String scheduleKind, Integer dayOfMonth,
                          Integer periodDays, LocalDate startDate) {
    }
}
