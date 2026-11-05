package com.paymentsapp.autopayments.topup;

import static com.paymentsapp.autopayments.error.AutopaymentsError.DESTINATION_INVALID;
import static com.paymentsapp.autopayments.error.AutopaymentsError.MONTHLY_LIMIT_BELOW_AMOUNT;
import static com.paymentsapp.autopayments.error.AutopaymentsError.MONTHLY_LIMIT_MIN;
import static com.paymentsapp.autopayments.error.AutopaymentsError.SAME_ACCOUNTS;
import static com.paymentsapp.autopayments.error.AutopaymentsError.SBP_BANK_REQUIRED;
import static com.paymentsapp.autopayments.error.AutopaymentsError.STATUS_INVALID;
import static com.paymentsapp.autopayments.error.AutopaymentsError.TARGET_BALANCE_MIN;
import static com.paymentsapp.autopayments.error.AutopaymentsError.THRESHOLD_REQUIRED;
import static com.paymentsapp.autopayments.error.AutopaymentsError.TOPUP_AMOUNT_MIN;
import static com.paymentsapp.autopayments.error.AutopaymentsError.TOPUP_KIND_REQUIRED;
import static com.paymentsapp.autopayments.error.AutopaymentsError.TOPUP_SOURCE_INVALID;

import com.paymentsapp.autopayments.autopay.ScheduleRules;
import com.paymentsapp.autopayments.client.Account;
import com.paymentsapp.autopayments.client.AccountClient;
import com.paymentsapp.autopayments.client.PricingClient;
import com.paymentsapp.autopayments.common.Status;
import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.autopayments.error.AutopaymentsError;
import com.paymentsapp.autopayments.topup.TopupRepository.NewTopup;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.text.Rubles;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TopupService {

    private final TopupRepository repo;
    private final AccountClient accounts;
    private final PricingClient pricing;
    private final ScheduleRules schedules;
    private final AutopayProperties props;
    private final Clock clock;

    public TopupService(TopupRepository repo, AccountClient accounts, PricingClient pricing, ScheduleRules schedules,
                        AutopayProperties props, Clock clock) {
        this.repo = repo;
        this.accounts = accounts;
        this.pricing = pricing;
        this.schedules = schedules;
        this.props = props;
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
        List<Account> own = accounts.accounts(userId);
        Account destination = own.stream().filter(a -> a.id().equals(req.destinationAccountId()) && a.usable())
                .findFirst().orElseThrow(() -> new DomainException(DESTINATION_INVALID));

        // Откуда: свой кошелёк / карта или свой счёт в другом банке по СБП
        SourceKind sourceKind = req.sourceKind() == null ? SourceKind.OWN : req.sourceKind();
        Account source;
        String bankId = null;
        if (sourceKind == SourceKind.SBP) {
            if (req.sourceBankId() == null || req.sourceBankName() == null) {
                throw new DomainException(SBP_BANK_REQUIRED);
            }
            source = accounts.sbpSource(userId, req.sourceBankId(), req.sourceBankName());
            bankId = req.sourceBankId();
        } else {
            source = own.stream().filter(a -> a.id().equals(req.sourceAccountId()) && a.usable()).findFirst()
                    .orElseThrow(() -> new DomainException(TOPUP_SOURCE_INVALID));
        }
        if (source.id().equals(destination.id())) {
            throw new DomainException(SAME_ACCOUNTS);
        }
        if (req.kind() == null) {
            throw new DomainException(TOPUP_KIND_REQUIRED);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate nextRun = null;
        switch (req.kind()) {
            case MONTHLY -> {
                atLeast(req.amount(), TOPUP_AMOUNT_MIN);
                nextRun = schedules.monthly(req.dayOfMonth(), today).nextOnOrAfter(today);
            }
            case BY_BALANCE -> {
                atLeast(req.amount(), TOPUP_AMOUNT_MIN);
                if (req.threshold() == null || req.threshold().signum() < 0) {
                    throw new DomainException(THRESHOLD_REQUIRED);
                }
            }
            case CONSTANT_BALANCE -> atLeast(req.targetBalance(), TARGET_BALANCE_MIN);
        }
        boolean monthly = req.kind() == TopupKind.MONTHLY;
        boolean constant = req.kind() == TopupKind.CONSTANT_BALANCE;
        if (req.monthlyLimit() != null) {
            atLeast(req.monthlyLimit(), MONTHLY_LIMIT_MIN);
            if (req.amount() != null && !constant && req.monthlyLimit().compareTo(req.amount()) < 0) {
                throw new DomainException(MONTHLY_LIMIT_BELOW_AMOUNT);
            }
        }
        // Есть ли тариф на такое пополнение (например, с карты другого банка — свой)
        BigDecimal probe = req.amount() != null ? req.amount() : req.targetBalance();
        pricing.ownTransferQuote(userId, source.id(), destination.id(), probe);

        String name = req.name() == null || req.name().isBlank() ? destination.displayTitle() : req.name().trim();
        UUID id = UUID.randomUUID();
        repo.insert(new NewTopup(id, userId, cut(name), sourceKind, source.id(), source.displayTitle(), bankId,
                destination.id(), destination.displayTitle(), req.kind(), monthly ? req.dayOfMonth() : null,
                constant ? null : req.amount(), req.kind() == TopupKind.BY_BALANCE ? req.threshold() : null,
                constant ? req.targetBalance() : null, monthly ? null : req.monthlyLimit(), nextRun,
                monthly ? null : OffsetDateTime.now(clock)));
        return repo.get(userId, id);
    }

    public Topup setStatus(UUID userId, UUID id, Status status) {
        if (status == null) {
            throw new DomainException(STATUS_INVALID);
        }
        Topup t = repo.get(userId, id);
        LocalDate today = LocalDate.now(clock);
        boolean monthly = t.kind() == TopupKind.MONTHLY;
        LocalDate next = t.nextRunDate();
        if (status == Status.ACTIVE && monthly && next.isBefore(today)) {
            next = schedules.monthly(t.dayOfMonth(), today).nextOnOrAfter(today);
        }
        repo.setStatus(id, status, next, monthly ? null : OffsetDateTime.now(clock));
        return repo.get(userId, id);
    }

    public void delete(UUID userId, UUID id) {
        repo.get(userId, id);
        repo.delete(id);
    }

    private void atLeast(BigDecimal value, AutopaymentsError error) {
        if (value == null || value.compareTo(props.minAmount()) < 0) {
            throw new DomainException(error, Rubles.format(props.minAmount()));
        }
    }

    private String cut(String name) {
        return name.length() > props.nameMax() ? name.substring(0, props.nameMax()) : name;
    }

    public record Request(String name, SourceKind sourceKind, UUID sourceAccountId, String sourceBankId, String sourceBankName,
                          UUID destinationAccountId, TopupKind kind, Integer dayOfMonth, BigDecimal amount,
                          BigDecimal threshold, BigDecimal targetBalance, BigDecimal monthlyLimit) {
    }
}
