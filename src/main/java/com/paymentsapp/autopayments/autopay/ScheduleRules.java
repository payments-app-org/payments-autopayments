package com.paymentsapp.autopayments.autopay;

import static com.paymentsapp.autopayments.error.AutopaymentsError.DAY_OF_MONTH_RANGE;
import static com.paymentsapp.autopayments.error.AutopaymentsError.PERIOD_RANGE;
import static com.paymentsapp.autopayments.error.AutopaymentsError.SCHEDULE_KIND_REQUIRED;
import static com.paymentsapp.autopayments.error.AutopaymentsError.START_DATE_RANGE;

import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.platform.error.DomainException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ValueRange;
import org.springframework.stereotype.Component;

// Расписание из запроса: число месяца, период и дата первого платежа — в пределах настроек autopay.*
@Component
public class ScheduleRules {

    private static final ValueRange DAYS_OF_MONTH = ChronoField.DAY_OF_MONTH.range();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AutopayProperties props;

    public ScheduleRules(AutopayProperties props) {
        this.props = props;
    }

    public Schedule of(ScheduleKind kind, Integer dayOfMonth, Integer periodDays, LocalDate startDate, LocalDate today) {
        if (kind == null) {
            throw new DomainException(SCHEDULE_KIND_REQUIRED);
        }
        return switch (kind) {
            case MONTHLY -> monthly(dayOfMonth, today);
            case PERIOD -> period(periodDays, startDate, today);
        };
    }

    public Schedule monthly(Integer dayOfMonth, LocalDate today) {
        if (dayOfMonth == null || !DAYS_OF_MONTH.isValidIntValue(dayOfMonth)) {
            throw new DomainException(DAY_OF_MONTH_RANGE, DAYS_OF_MONTH.getMinimum(), DAYS_OF_MONTH.getMaximum());
        }
        return Schedule.monthly(dayOfMonth, today);
    }

    private Schedule period(Integer periodDays, LocalDate startDate, LocalDate today) {
        if (periodDays == null || periodDays < 1 || periodDays > props.maxPeriodDays()) {
            throw new DomainException(PERIOD_RANGE, props.maxPeriodDays());
        }
        LocalDate first = startDate == null ? today : startDate;
        LocalDate latest = today.plus(props.maxStartAhead());
        if (first.isBefore(today) || first.isAfter(latest)) {
            throw new DomainException(START_DATE_RANGE, DATE.format(latest));
        }
        return Schedule.period(periodDays, first);
    }
}
