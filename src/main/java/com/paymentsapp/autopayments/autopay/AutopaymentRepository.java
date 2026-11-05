package com.paymentsapp.autopayments.autopay;

import static com.paymentsapp.autopayments.error.AutopaymentsError.AUTOPAYMENT_NOT_FOUND;

import com.paymentsapp.autopayments.autopay.Autopayment.Run;
import com.paymentsapp.autopayments.common.Columns;
import com.paymentsapp.autopayments.common.RunStatus;
import com.paymentsapp.autopayments.common.Status;
import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.autopayments.template.Target;
import com.paymentsapp.autopayments.template.TemplateRepository;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
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

    private static final TypeReference<Map<String, String>> FIELDS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final AutopayProperties props;

    public AutopaymentRepository(JdbcClient jdbc, JsonMapper json, AutopayProperties props) {
        this.jdbc = jdbc;
        this.json = json;
        this.props = props;
    }

    public List<Autopayment> list(UUID userId) {
        return jdbc.sql("select * from autopayments where user_id = :user order by created_at").param("user", userId)
                .query(this::map).list();
    }

    public Autopayment get(UUID userId, UUID id) {
        return jdbc.sql("select * from autopayments where id = :id and user_id = :user").param("id", id).param("user", userId)
                .query(this::map).optional().orElseThrow(() -> new DomainException(AUTOPAYMENT_NOT_FOUND));
    }

    public Optional<Autopayment> get(UUID id) {
        return jdbc.sql("select * from autopayments where id = :id").param("id", id).query(this::map).optional();
    }

    public void insert(UUID id, UUID userId, String name, Target.Checked target, BigDecimal amount, UUID sourceAccountId,
                       UUID backupAccountId, Schedule schedule, LocalDate nextRunDate) {
        jdbc.sql("""
                        insert into autopayments (id, user_id, name, provider_code, provider_group, provider_name, provider_domain,
                            provider_color, provider_country, fields, target, amount, source_account_id, backup_account_id,
                            schedule_kind, day_of_month, period_days, start_date, next_run_date)
                        values (:id, :user, :name, :code, :group, :pname, :domain, :color, :country, cast(:fields as jsonb),
                            :target, :amount, :source, :backup, :kind, :day, :period, :start, :next)
                        """)
                .param("id", id).param("user", userId).param("name", name)
                .param("code", target.provider().code()).param("group", target.provider().groupCode())
                .param("pname", target.provider().name()).param("domain", target.provider().domain())
                .param("color", target.provider().color()).param("country", target.provider().countryCode())
                .param("fields", json.writeValueAsString(target.fields())).param("target", target.target())
                .param("amount", amount).param("source", sourceAccountId).param("backup", backupAccountId)
                .param("kind", schedule.kind().name()).param("day", schedule.dayOfMonth())
                .param("period", schedule.periodDays()).param("start", schedule.startDate()).param("next", nextRunDate)
                .update();
    }

    public void setStatus(UUID id, Status status, LocalDate nextRunDate) {
        jdbc.sql("update autopayments set status = :status, next_run_date = :next, updated_at = now() where id = :id")
                .param("id", id).param("status", status.name()).param("next", nextRunDate).update();
    }

    public void delete(UUID id) {
        jdbc.sql("delete from autopayments where id = :id").param("id", id).update();
    }

    // Пачка активных автоплатежей, у которых наступила дата. Под бронирует их за собой (claimed_until):
    // другие поды пропускают занятые строки (SKIP LOCKED) и не берут их, пока бронь не истечёт — работа делится, а не дублируется.
    // Не довели (сосед недоступен) — запись вернётся в работу, когда истечёт бронь.
    public List<UUID> claimDue(LocalDate today, OffsetDateTime now) {
        return jdbc.sql("""
                        update autopayments set claimed_until = :until
                        where id in (
                            select id from autopayments
                            where status = :active and next_run_date <= :today
                                and (claimed_until is null or claimed_until <= :now)
                            order by next_run_date
                            limit :batch
                            for update skip locked)
                        returning id
                        """)
                .param("active", Status.ACTIVE.name()).param("today", today).param("now", now)
                .param("until", now.plus(props.claimFor())).param("batch", props.batchSize())
                .query(UUID.class).list();
    }

    public List<Run> runs(UUID autopaymentId) {
        return jdbc.sql("select * from autopayment_runs where autopayment_id = :id order by run_date desc limit :shown")
                .param("id", autopaymentId).param("shown", props.runsShown()).query(AutopaymentRepository::run).list();
    }

    // Запуск на дату: создаём, если его ещё нет. Уникальность (autopayment_id, run_date) не даст запустить дважды.
    public Run claim(Autopayment a, LocalDate runDate) {
        jdbc.sql("""
                        insert into autopayment_runs (id, autopayment_id, run_date, status, amount)
                        values (:id, :ap, :date, :pending, :amount)
                        on conflict (autopayment_id, run_date) do nothing
                        """)
                .param("id", UUID.randomUUID()).param("ap", a.id()).param("date", runDate)
                .param("pending", RunStatus.PENDING.name()).param("amount", a.amount())
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

    // Итог запуска и следующая дата автоплатежа; бронь снимаем
    public void finish(Autopayment a, Run run, RunStatus status, UUID paymentId, BigDecimal commission, String message,
                       LocalDate nextRunDate) {
        jdbc.sql("""
                        update autopayment_runs set status = :status, payment_id = :payment, commission = :commission,
                            message = :message, finished_at = now()
                        where id = :id
                        """)
                .param("id", run.id()).param("status", status.name()).param("payment", paymentId)
                .param("commission", commission).param("message", message).update();
        jdbc.sql("""
                        update autopayments set next_run_date = :next, last_run_date = :date, last_status = :status,
                            last_message = :message, claimed_until = null, updated_at = now()
                        where id = :id
                        """)
                .param("id", a.id()).param("next", nextRunDate).param("date", run.runDate()).param("status", status.name())
                .param("message", message).update();
    }

    private Autopayment map(ResultSet rs, int n) throws SQLException {
        return new Autopayment(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("name"),
                TemplateRepository.provider(rs), json.readValue(rs.getString("fields"), FIELDS), rs.getString("target"),
                rs.getBigDecimal("amount"), rs.getObject("source_account_id", UUID.class),
                rs.getObject("backup_account_id", UUID.class), ScheduleKind.valueOf(rs.getString("schedule_kind")),
                (Integer) rs.getObject("day_of_month"), (Integer) rs.getObject("period_days"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("next_run_date", LocalDate.class),
                Status.valueOf(rs.getString("status")), rs.getObject("last_run_date", LocalDate.class),
                Columns.enumOrNull(rs, "last_status", RunStatus.class), rs.getString("last_message"));
    }

    private static Run run(ResultSet rs, int n) throws SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getObject("run_date", LocalDate.class),
                RunStatus.valueOf(rs.getString("status")), rs.getObject("payment_id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getBigDecimal("amount"), rs.getBigDecimal("commission"),
                rs.getString("message"), rs.getObject("finished_at", OffsetDateTime.class));
    }
}
