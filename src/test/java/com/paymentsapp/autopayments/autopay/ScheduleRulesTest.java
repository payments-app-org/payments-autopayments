package com.paymentsapp.autopayments.autopay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentsapp.autopayments.error.AutopaymentsError;
import com.paymentsapp.autopayments.support.TestProps;
import com.paymentsapp.platform.error.DomainException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ScheduleRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private final ScheduleRules rules = new ScheduleRules(TestProps.autopay());

    private static AutopaymentsError codeOf(Throwable e) {
        return (AutopaymentsError) ((DomainException) e).getCode();
    }

    @Test
    void dayOfMonthWithinCalendar() {
        assertThat(rules.of(ScheduleKind.MONTHLY, 31, null, null, TODAY).dayOfMonth()).isEqualTo(31);
        assertThatThrownBy(() -> rules.of(ScheduleKind.MONTHLY, 32, null, null, TODAY))
                .hasMessage("Число месяца — от 1 до 31");
        assertThatThrownBy(() -> rules.of(ScheduleKind.MONTHLY, null, null, null, TODAY))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AutopaymentsError.DAY_OF_MONTH_RANGE));
    }

    @Test
    void periodAndFirstDateWithinSettings() {
        assertThat(rules.of(ScheduleKind.PERIOD, null, 180, null, TODAY).startDate()).isEqualTo(TODAY);
        assertThatThrownBy(() -> rules.of(ScheduleKind.PERIOD, null, 181, null, TODAY))
                .hasMessage("Период — от 1 до 180 дней");
        assertThatThrownBy(() -> rules.of(ScheduleKind.PERIOD, null, 7, TODAY.minusDays(1), TODAY))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AutopaymentsError.START_DATE_RANGE));
        assertThatThrownBy(() -> rules.of(ScheduleKind.PERIOD, null, 7, TODAY.plusYears(1).plusDays(1), TODAY))
                .hasMessage("Первый платёж — с сегодняшнего дня и не позже 05.11.2027");
    }

    @Test
    void kindIsRequired() {
        assertThatThrownBy(() -> rules.of(null, 1, null, null, TODAY))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AutopaymentsError.SCHEDULE_KIND_REQUIRED));
    }
}
