package com.paymentsapp.autopayments.topup;

import static com.paymentsapp.autopayments.error.AutopaymentsError.TOPUP_NOT_FOUND;

import com.paymentsapp.autopayments.common.Columns;
import com.paymentsapp.autopayments.common.RunStatus;
import com.paymentsapp.autopayments.common.Status;
import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.autopayments.topup.Topup.Run;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TopupRepository {

    // В лимит месяца идут успешные и ещё не завершённые запуски
    private static final List<String> COUNTED_IN_LIMIT = Stream.of(RunStatus.SUCCESS, RunStatus.PROCESSING, RunStatus.PENDING)
            .map(Enum::name).toList();

    // Сколько пополнено с начала текущего месяца (по часовому поясу бизнеса)
    private static final String SELECT = """
            select t.*, coalesce((select sum(r.amount) from topup_runs r where r.topup_id = t.id
                and r.status in (:counted) and r.created_at >= :monthStart), 0) as month_used
            from topups t
            """;

    private final JdbcClient jdbc;
    private final AutopayProperties props;
    private final Clock clock;

    public TopupRepository(JdbcClient jdbc, AutopayProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    private OffsetDateTime monthStart() {
        return LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
    }

    private JdbcClient.StatementSpec select(String where) {
        return jdbc.sql(SELECT + where).param("counted", COUNTED_IN_LIMIT).param("monthStart", monthStart());
    }

    public List<Topup> list(UUID userId) {
        return select("where t.user_id = :user order by t.created_at").param("user", userId)
                .query(TopupRepository::map).list();
    }

    public Topup get(UUID userId, UUID id) {
        return select("where t.id = :id and t.user_id = :user").param("id", id).param("user", userId)
                .query(TopupRepository::map).optional().orElseThrow(() -> new DomainException(TOPUP_NOT_FOUND));
    }

    public Optional<Topup> get(UUID id) {
        return select("where t.id = :id").param("id", id).query(TopupRepository::map).optional();
    }

    public void insert(NewTopup t) {
        jdbc.sql("""
                        insert into topups (id, user_id, name, source_kind, source_account_id, source_title, source_bank_id,
                            destination_account_id, destination_title, kind, day_of_month, amount, threshold, target_balance,
                            monthly_limit, next_run_date, next_check_at)
                        values (:id, :user, :name, :skind, :source, :stitle, :bank, :dest, :dtitle, :kind, :day, :amount,
                            :threshold, :target, :limit, :next, :check)
                        """)
                .param("id", t.id()).param("user", t.userId()).param("name", t.name())
                .param("skind", t.sourceKind().name()).param("source", t.sourceAccountId())
                .param("stitle", t.sourceTitle()).param("bank", t.sourceBankId()).param("dest", t.destinationAccountId())
                .param("dtitle", t.destinationTitle()).param("kind", t.kind().name()).param("day", t.dayOfMonth())
                .param("amount", t.amount()).param("threshold", t.threshold()).param("target", t.targetBalance())
                .param("limit", t.monthlyLimit()).param("next", t.nextRunDate()).param("check", t.nextCheckAt())
                .update();
    }

    public void setStatus(UUID id, Status status, LocalDate nextRunDate, OffsetDateTime nextCheckAt) {
        jdbc.sql("update topups set status = :status, next_run_date = :next, next_check_at = :check, updated_at = now() where id = :id")
                .param("id", id).param("status", status.name()).param("next", nextRunDate).param("check", nextCheckAt)
                .update();
    }

    public void delete(UUID id) {
        jdbc.sql("delete from topups where id = :id").param("id", id).update();
    }

    // Раз в месяц — у которых наступил день; по балансу — у которых подошло время проверки.
    // Бронь, как у автоплатежей: под забирает пачку себе, другие поды её пропускают.
    public List<UUID> claimDue(LocalDate today, OffsetDateTime now) {
        return jdbc.sql("""
                        update topups set claimed_until = :until
                        where id in (
                            select id from topups
                            where status = :active
                                and ((kind = :monthly and next_run_date <= :today)
                                    or (kind <> :monthly and (next_check_at is null or next_check_at <= :now)))
                                and (claimed_until is null or claimed_until <= :now)
                            order by created_at
                            limit :batch
                            for update skip locked)
                        returning id
                        """)
                .param("active", Status.ACTIVE.name()).param("monthly", TopupKind.MONTHLY.name())
                .param("today", today).param("now", now).param("until", now.plus(props.claimFor()))
                .param("batch", props.batchSize())
                .query(UUID.class).list();
    }

    public List<Run> runs(UUID topupId) {
        return jdbc.sql("select * from topup_runs where topup_id = :id order by created_at desc limit :shown")
                .param("id", topupId).param("shown", props.runsShown()).query(TopupRepository::run).list();
    }

    // Незавершённый запуск (сбой связи посреди пополнения) — его доводим тем же ключом
    public Optional<Run> pending(UUID topupId) {
        return jdbc.sql("select * from topup_runs where topup_id = :id and status = :pending order by created_at limit 1")
                .param("id", topupId).param("pending", RunStatus.PENDING.name()).query(TopupRepository::run).optional();
    }

    public Run claim(UUID topupId, String triggerKey, BigDecimal amount, BigDecimal balanceBefore) {
        jdbc.sql("""
                        insert into topup_runs (id, topup_id, trigger_key, status, amount, balance_before)
                        values (:id, :topup, :key, :pending, :amount, :balance)
                        on conflict (topup_id, trigger_key) do nothing
                        """)
                .param("id", UUID.randomUUID()).param("topup", topupId).param("key", triggerKey)
                .param("pending", RunStatus.PENDING.name()).param("amount", amount).param("balance", balanceBefore)
                .update();
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

    // Итог запуска, следующая дата (раз в месяц) или время проверки баланса; бронь снимаем
    public void finish(Topup t, Run run, RunStatus status, UUID paymentId, BigDecimal commission, String message,
                       LocalDate nextRunDate, OffsetDateTime nextCheckAt) {
        jdbc.sql("""
                        update topup_runs set status = :status, payment_id = :payment, commission = :commission, message = :message,
                            finished_at = now()
                        where id = :id
                        """)
                .param("id", run.id()).param("status", status.name()).param("payment", paymentId)
                .param("commission", commission).param("message", message).update();
        jdbc.sql("""
                        update topups set last_run_at = now(), last_status = :status, last_message = :message,
                            next_run_date = coalesce(:next, next_run_date), next_check_at = :check, claimed_until = null,
                            updated_at = now()
                        where id = :id
                        """)
                .param("id", t.id()).param("status", status.name()).param("message", message).param("next", nextRunDate)
                .param("check", nextCheckAt).update();
    }

    public void postponeCheck(UUID id, OffsetDateTime at) {
        jdbc.sql("update topups set next_check_at = :at, claimed_until = null where id = :id")
                .param("id", id).param("at", at).update();
    }

    private static Topup map(ResultSet rs, int n) throws SQLException {
        return new Topup(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("name"),
                SourceKind.valueOf(rs.getString("source_kind")), rs.getObject("source_account_id", UUID.class),
                rs.getString("source_title"), rs.getString("source_bank_id"),
                rs.getObject("destination_account_id", UUID.class), rs.getString("destination_title"),
                TopupKind.valueOf(rs.getString("kind")), (Integer) rs.getObject("day_of_month"),
                rs.getBigDecimal("amount"), rs.getBigDecimal("threshold"), rs.getBigDecimal("target_balance"),
                rs.getBigDecimal("monthly_limit"), rs.getObject("next_run_date", LocalDate.class),
                rs.getObject("next_check_at", OffsetDateTime.class), Status.valueOf(rs.getString("status")),
                rs.getObject("last_run_at", OffsetDateTime.class), Columns.enumOrNull(rs, "last_status", RunStatus.class),
                rs.getString("last_message"), rs.getBigDecimal("month_used"));
    }

    private static Run run(ResultSet rs, int n) throws SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getString("trigger_key"), RunStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("amount"), rs.getBigDecimal("balance_before"), rs.getObject("payment_id", UUID.class),
                rs.getBigDecimal("commission"), rs.getString("message"), rs.getObject("created_at", OffsetDateTime.class));
    }

    // Новое автопополнение: поля, не нужные его виду, — null (так требуют ограничения таблицы)
    public record NewTopup(UUID id, UUID userId, String name, SourceKind sourceKind, UUID sourceAccountId, String sourceTitle,
                           String sourceBankId, UUID destinationAccountId, String destinationTitle, TopupKind kind,
                           Integer dayOfMonth, BigDecimal amount, BigDecimal threshold, BigDecimal targetBalance,
                           BigDecimal monthlyLimit, LocalDate nextRunDate, OffsetDateTime nextCheckAt) {
    }
}
