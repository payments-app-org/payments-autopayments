package com.paymentsapp.autopayments.autopay;

import com.paymentsapp.autopayments.autopay.Autopayment.Run;
import com.paymentsapp.autopayments.template.Template.ProviderView;
import com.paymentsapp.autopayments.web.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AutopaymentRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AutopaymentRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Autopayment> list(UUID userId) {
        return jdbc.sql("select * from autopayments where user_id = :user order by created_at").param("user", userId)
                .query(this::map).list();
    }

    public Autopayment get(UUID userId, UUID id) {
        return jdbc.sql("select * from autopayments where id = :id and user_id = :user").param("id", id).param("user", userId)
                .query(this::map).optional().orElseThrow(() -> ApiException.notFound("Автоплатёж не найден"));
    }

    public Optional<Autopayment> get(UUID id) {
        return jdbc.sql("select * from autopayments where id = :id").param("id", id).query(this::map).optional();
    }

    // Активные автоплатежи, у которых наступила дата
    public List<UUID> due(LocalDate today) {
        return jdbc.sql("select id from autopayments where status = 'ACTIVE' and next_run_date <= :today order by next_run_date limit 50")
                .param("today", today).query(UUID.class).list();
    }

    public List<Run> runs(UUID autopaymentId) {
        return jdbc.sql("select * from autopayment_runs where autopayment_id = :id order by run_date desc limit 20")
                .param("id", autopaymentId).query(AutopaymentRepository::run).list();
    }

    // Запуск на дату: создаём, если его ещё нет. Уникальность (autopayment_id, run_date) не даст запустить дважды.
    public Run claim(Autopayment a, LocalDate runDate) {
        jdbc.sql("""
                        insert into autopayment_runs (id, autopayment_id, run_date, status, amount)
                        values (:id, :ap, :date, 'PENDING', :amount)
                        on conflict (autopayment_id, run_date) do nothing
                        """)
                .param("id", UUID.randomUUID()).param("ap", a.id()).param("date", runDate).param("amount", a.amount())
                .update();
        return jdbc.sql("select * from autopayment_runs where autopayment_id = :ap and run_date = :date")
                .param("ap", a.id()).param("date", runDate).query(AutopaymentRepository::run).single();
    }

    public Optional<String> quoteToken(UUID runId, UUID accountId) {
        return jdbc.sql("select quote_token from autopayment_runs where id = :id and account_id = :account and quote_token is not null")
                .param("id", runId).param("account", accountId).query(String.class).optional();
    }

    public void attempt(UUID runId, UUID accountId, String quoteToken) {
        jdbc.sql("update autopayment_runs set account_id = :account, quote_token = :token where id = :id")
                .param("id", runId).param("account", accountId).param("token", quoteToken).update();
    }

    // Итог запуска и следующая дата автоплатежа
    public void finish(Autopayment a, Run run, String status, UUID paymentId, java.math.BigDecimal commission, String message,
                       LocalDate nextRunDate) {
        jdbc.sql("""
                        update autopayment_runs set status = :status, payment_id = :payment, commission = :commission,
                            message = :message, finished_at = now()
                        where id = :id
                        """)
                .param("id", run.id()).param("status", status).param("payment", paymentId).param("commission", commission)
                .param("message", message).update();
        jdbc.sql("""
                        update autopayments set next_run_date = :next, last_run_date = :date, last_status = :status,
                            last_message = :message, updated_at = now()
                        where id = :id
                        """)
                .param("id", a.id()).param("next", nextRunDate).param("date", run.runDate()).param("status", status)
                .param("message", message).update();
    }

    private Autopayment map(ResultSet rs, int n) throws SQLException {
        return new Autopayment(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("name"),
                new ProviderView(rs.getString("provider_code"), rs.getString("provider_group"), rs.getString("provider_name"),
                        rs.getString("provider_domain"), rs.getString("provider_color"), rs.getString("provider_country")),
                json.readValue(rs.getString("fields"), new TypeReference<Map<String, String>>() { }), rs.getString("target"),
                rs.getBigDecimal("amount"), rs.getObject("source_account_id", UUID.class),
                rs.getObject("backup_account_id", UUID.class), rs.getString("schedule_kind"),
                (Integer) rs.getObject("day_of_month"), (Integer) rs.getObject("period_days"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("next_run_date", LocalDate.class),
                rs.getString("status"), rs.getObject("last_run_date", LocalDate.class), rs.getString("last_status"),
                rs.getString("last_message"));
    }

    private static Run run(ResultSet rs, int n) throws SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getObject("run_date", LocalDate.class), rs.getString("status"),
                rs.getObject("payment_id", UUID.class), rs.getObject("account_id", UUID.class), rs.getBigDecimal("amount"),
                rs.getBigDecimal("commission"), rs.getString("message"), rs.getObject("finished_at", OffsetDateTime.class));
    }
}
