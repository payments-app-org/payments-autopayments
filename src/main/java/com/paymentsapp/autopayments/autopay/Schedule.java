package com.paymentsapp.autopayments.autopay;

import com.paymentsapp.autopayments.web.ApiException;
import java.time.LocalDate;
import java.time.YearMonth;

// Расписание автоплатежа: каждый месяц в выбранное число (в коротком месяце — в последний день) или каждые N дней
public record Schedule(String kind, Integer dayOfMonth, Integer periodDays, LocalDate startDate) {

    public static Schedule of(String kind, Integer dayOfMonth, Integer periodDays, LocalDate startDate, LocalDate today) {
        if ("MONTHLY".equals(kind)) {
            if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31) {
                throw ApiException.badRequest("Число месяца — от 1 до 31");
            }
            return new Schedule(kind, dayOfMonth, null, today);
        }
        if ("PERIOD".equals(kind)) {
            if (periodDays == null || periodDays < 1 || periodDays > 180) {
                throw ApiException.badRequest("Период — от 1 до 180 дней");
            }
            LocalDate start = startDate == null ? today : startDate;
            if (start.isBefore(today) || start.isAfter(today.plusYears(1))) {
                throw ApiException.badRequest("Первый платёж — с сегодняшнего дня и не позже чем через год");
            }
            return new Schedule(kind, null, periodDays, start);
        }
        throw ApiException.badRequest("Выберите вид автоплатежа: каждый месяц или по периоду");
    }

    // Ближайшая дата платежа не раньше from
    public LocalDate nextOnOrAfter(LocalDate from) {
        if ("MONTHLY".equals(kind)) {
            LocalDate d = dayIn(YearMonth.from(from));
            return d.isBefore(from) ? dayIn(YearMonth.from(from).plusMonths(1)) : d;
        }
        if (!from.isAfter(startDate)) {
            return startDate;
        }
        long steps = (java.time.temporal.ChronoUnit.DAYS.between(startDate, from) + periodDays - 1) / periodDays;
        return startDate.plusDays(steps * periodDays);
    }

    // Следующая дата после состоявшегося платежа
    public LocalDate after(LocalDate run) {
        return "MONTHLY".equals(kind) ? dayIn(YearMonth.from(run).plusMonths(1)) : run.plusDays(periodDays);
    }

    private LocalDate dayIn(YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }
}
