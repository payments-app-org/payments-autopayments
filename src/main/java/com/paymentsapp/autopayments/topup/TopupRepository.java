package com.paymentsapp.autopayments.topup;

import com.paymentsapp.autopayments.topup.Topup.Run;
import com.paymentsapp.autopayments.web.ApiException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TopupRepository {

    // Сколько пополнено с начала текущего месяца (по Москве): успешные и ещё не завершённые запуски
    private static final String SELECT = """
            select t.*, coalesce((select sum(r.amount) from topup_runs r where r.topup_id = t.id
                and r.status in ('SUCCESS', 'PROCESSING', 'PENDING')
                and r.created_at >= :monthStart), 0) as month_used
            from topups t
            """;

    private final JdbcClient jdbc;
    private final ZoneId zone;

    public TopupRepository(JdbcClient jdbc, @Value("${autopay.zone}") String zone) {
        this.jdbc = jdbc;
        this.zone = ZoneId.of(zone);
    }

    private OffsetDateTime monthStart() {
        return LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toOffsetDateTime();
    }

    public List<Topup> list(UUID userId) {
        return jdbc.sql(SELECT + "where t.user_id = :user order by t.created_at")
                .param("user", userId).param("monthStart", monthStart()).query(TopupRepository::map).list();
    }

    public Topup get(UUID userId, UUID id) {
        return jdbc.sql(SELECT + "where t.id = :id and t.user_id = :user").param("id", id).param("user", userId)
                .param("monthStart", monthStart()).query(TopupRepository::map).optional()
                .orElseThrow(() -> ApiException.notFound("Автопополнение не найдено"));
    }

    public Optional<Topup> get(UUID id) {
        return jdbc.sql(SELECT + "where t.id = :id").param("id", id).param("monthStart", monthStart())
                .query(TopupRepository::map).optional();
    }

    // Раз в месяц — у которых наступил день; по балансу — у которых подошло время проверки
    public List<UUID> due(LocalDate today, OffsetDateTime now) {
        return jdbc.sql("""
                        select id from topups where status = 'ACTIVE' and (
                            (kind = 'MONTHLY' and next_run_date <= :today)
                            or (kind <> 'MONTHLY' and (next_check_at is null or next_check_at <= :now)))
                        order by created_at limit 100
                        """)
                .param("today", today).param("now", now).query(UUID.class).list();
    }

    public List<Run> runs(UUID topupId) {
        return jdbc.sql("select * from topup_runs where topup_id = :id order by created_at desc limit 20")
                .param("id", topupId).query(TopupRepository::run).list();
    }

    // Незавершённый запуск (сбой связи посреди пополнения) — его доводим тем же ключом
    public Optional<Run> pending(UUID topupId) {
        return jdbc.sql("select * from topup_runs where topup_id = :id and status = 'PENDING' order by created_at limit 1")
                .param("id", topupId).query(TopupRepository::run).optional();
    }

    public Run claim(UUID topupId, String triggerKey, BigDecimal amount, BigDecimal balanceBefore) {
        jdbc.sql("""
                        insert into topup_runs (id, topup_id, trigger_key, status, amount, balance_before)
                        values (:id, :topup, :key, 'PENDING', :amount, :balance)
                        on conflict (topup_id, trigger_key) do nothing
                        """)
                .param("id", UUID.randomUUID()).param("topup", topupId).param("key", triggerKey)
                .param("amount", amount).param("balance", balanceBefore).update();
        return jdbc.sql("select * from topup_runs where topup_id = :topup and trigger_key = :key")
                .param("topup", topupId).param("key", triggerKey).query(TopupRepository::run).single();
    }

    public Optional<String> quoteToken(UUID runId) {
        return jdbc.sql("select quote_token from topup_runs where id = :id and quote_token is not null").param("id", runId)
                .query(String.class).optional();
    }

    public void saveQuote(UUID runId, String token) {
        jdbc.sql("update topup_runs set quote_token = :token where id = :id").param("id", runId).param("token", token).update();
    }

    public void finish(Topup t, Run run, String status, UUID paymentId, BigDecimal commission, String message,
                       LocalDate nextRunDate, OffsetDateTime nextCheckAt) {
        jdbc.sql("""
                        update topup_runs set status = :status, payment_id = :payment, commission = :commission, message = :message,
                            finished_at = now()
                        where id = :id
                        """)
                .param("id", run.id()).param("status", status).param("payment", paymentId).param("commission", commission)
                .param("message", message).update();
        jdbc.sql("""
                        update topups set last_run_at = now(), last_status = :status, last_message = :message,
                            next_run_date = coalesce(:next, next_run_date), next_check_at = :check, updated_at = now()
                        where id = :id
                        """)
                .param("id", t.id()).param("status", status).param("message", message).param("next", nextRunDate)
                .param("check", nextCheckAt).update();
    }

    public void postponeCheck(UUID id, OffsetDateTime at) {
        jdbc.sql("update topups set next_check_at = :at where id = :id").param("id", id).param("at", at).update();
    }

    private static Topup map(ResultSet rs, int n) throws SQLException {
        return new Topup(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("name"),
                rs.getString("source_kind"), rs.getObject("source_account_id", UUID.class), rs.getString("source_title"),
                rs.getString("source_bank_id"), rs.getObject("destination_account_id", UUID.class),
                rs.getString("destination_title"), rs.getString("kind"), (Integer) rs.getObject("day_of_month"),
                rs.getBigDecimal("amount"), rs.getBigDecimal("threshold"), rs.getBigDecimal("target_balance"),
                rs.getBigDecimal("monthly_limit"), rs.getObject("next_run_date", LocalDate.class),
                rs.getObject("next_check_at", OffsetDateTime.class), rs.getString("status"),
                rs.getObject("last_run_at", OffsetDateTime.class), rs.getString("last_status"), rs.getString("last_message"),
                rs.getBigDecimal("month_used"));
    }

    private static Run run(ResultSet rs, int n) throws SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getString("trigger_key"), rs.getString("status"),
                rs.getBigDecimal("amount"), rs.getBigDecimal("balance_before"), rs.getObject("payment_id", UUID.class),
                rs.getBigDecimal("commission"), rs.getString("message"), rs.getObject("created_at", OffsetDateTime.class));
    }
}
