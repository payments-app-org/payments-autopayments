package com.paymentsapp.autopayments.autopay;

import static com.paymentsapp.autopayments.error.AutopaymentsError.BACKUP_SAME_AS_SOURCE;
import static com.paymentsapp.autopayments.error.AutopaymentsError.STATUS_INVALID;

import com.paymentsapp.autopayments.common.Status;
import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.autopayments.template.Target;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AutopaymentService {

    private static final String NAME_SEPARATOR = " · ";

    private final AutopaymentRepository repo;
    private final Target target;
    private final ScheduleRules schedules;
    private final AutopayProperties props;
    private final Clock clock;

    public AutopaymentService(AutopaymentRepository repo, Target target, ScheduleRules schedules, AutopayProperties props,
                              Clock clock) {
        this.repo = repo;
        this.target = target;
        this.schedules = schedules;
        this.props = props;
        this.clock = clock;
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
            throw new DomainException(BACKUP_SAME_AS_SOURCE);
        }
        List<UUID> accounts = new ArrayList<>();
        accounts.add(req.sourceAccountId());
        if (req.backupAccountId() != null) {
            accounts.add(req.backupAccountId());
        }
        Target.Checked t = target.check(userId, req.providerCode(), req.fields(), req.amount(), accounts);
        LocalDate today = LocalDate.now(clock);
        Schedule s = schedules.of(req.scheduleKind(), req.dayOfMonth(), req.periodDays(), req.startDate(), today);
        String name = req.name() == null || req.name().isBlank()
                ? t.provider().name() + (t.target() != null ? NAME_SEPARATOR + t.target() : "")
                : req.name().trim();
        UUID id = UUID.randomUUID();
        repo.insert(id, userId, cut(name), t, req.amount(), req.sourceAccountId(), req.backupAccountId(), s,
                s.nextOnOrAfter(today));
        return repo.get(userId, id);
    }

    // Приостановить / возобновить. После паузы платим со следующей даты по расписанию, пропущенные не догоняем.
    public Autopayment setStatus(UUID userId, UUID id, Status status) {
        if (status == null) {
            throw new DomainException(STATUS_INVALID);
        }
        Autopayment a = repo.get(userId, id);
        LocalDate today = LocalDate.now(clock);
        LocalDate next = a.nextRunDate();
        if (status == Status.ACTIVE && next.isBefore(today)) {
            next = a.schedule().nextOnOrAfter(today);
        }
        repo.setStatus(id, status, next);
        return repo.get(userId, id);
    }

    public void delete(UUID userId, UUID id) {
        repo.get(userId, id);
        repo.delete(id);
    }

    private String cut(String name) {
        return name.length() > props.nameMax() ? name.substring(0, props.nameMax()) : name;
    }

    public record Request(String name, String providerCode, Map<String, String> fields, BigDecimal amount,
                          UUID sourceAccountId, UUID backupAccountId, ScheduleKind scheduleKind, Integer dayOfMonth,
                          Integer periodDays, LocalDate startDate) {
    }
}
