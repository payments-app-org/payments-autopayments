package com.paymentsapp.autopayments.autopay;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

// Расписание: каждый месяц в выбранное число или каждые periodDays дней начиная со startDate.
// Проверку значений из запроса делает ScheduleRules.
public record Schedule(ScheduleKind kind, Integer dayOfMonth, Integer periodDays, LocalDate startDate) {

    public static Schedule monthly(int dayOfMonth, LocalDate today) {
        return new Schedule(ScheduleKind.MONTHLY, dayOfMonth, null, today);
    }

    public static Schedule period(int periodDays, LocalDate startDate) {
        return new Schedule(ScheduleKind.PERIOD, null, periodDays, startDate);
    }

    // Ближайшая дата платежа не раньше from
    public LocalDate nextOnOrAfter(LocalDate from) {
        if (kind == ScheduleKind.MONTHLY) {
            LocalDate d = dayIn(YearMonth.from(from));
            return d.isBefore(from) ? dayIn(YearMonth.from(from).plusMonths(1)) : d;
        }
        if (!from.isAfter(startDate)) {
            return startDate;
        }
        long steps = (ChronoUnit.DAYS.between(startDate, from) + periodDays - 1) / periodDays;
        return startDate.plusDays(steps * periodDays);
    }

    // Следующая дата после состоявшегося платежа
    public LocalDate after(LocalDate run) {
        return kind == ScheduleKind.MONTHLY ? dayIn(YearMonth.from(run).plusMonths(1)) : run.plusDays(periodDays);
    }

    private LocalDate dayIn(YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }
}
