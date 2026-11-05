package com.paymentsapp.autopayments.autopay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ScheduleTest {

    @Test
    void monthlyOnLastDayInShortMonth() {
        Schedule s = Schedule.monthly(31, LocalDate.of(2026, 1, 10));

        assertThat(s.nextOnOrAfter(LocalDate.of(2026, 2, 1))).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(s.after(LocalDate.of(2026, 2, 28))).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void monthlyMovesToNextMonthWhenDayHasPassed() {
        Schedule s = Schedule.monthly(5, LocalDate.of(2026, 11, 10));

        assertThat(s.nextOnOrAfter(LocalDate.of(2026, 11, 10))).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(s.nextOnOrAfter(LocalDate.of(2026, 11, 5))).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void periodStepsFromStartDate() {
        Schedule s = Schedule.period(10, LocalDate.of(2026, 11, 1));

        assertThat(s.nextOnOrAfter(LocalDate.of(2026, 10, 20))).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(s.nextOnOrAfter(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(s.nextOnOrAfter(LocalDate.of(2026, 11, 11))).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(s.after(LocalDate.of(2026, 11, 11))).isEqualTo(LocalDate.of(2026, 11, 21));
    }
}
