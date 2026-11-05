package com.paymentsapp.autopayments;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentsapp.autopayments.autopay.Autopayment;
import com.paymentsapp.autopayments.autopay.AutopaymentRepository;
import com.paymentsapp.autopayments.autopay.Schedule;
import com.paymentsapp.autopayments.client.Provider;
import com.paymentsapp.autopayments.common.RunStatus;
import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.autopayments.support.TestProps;
import com.paymentsapp.autopayments.template.Target;
import com.paymentsapp.autopayments.topup.SourceKind;
import com.paymentsapp.autopayments.topup.Topup;
import com.paymentsapp.autopayments.topup.TopupKind;
import com.paymentsapp.autopayments.topup.TopupRepository;
import com.paymentsapp.autopayments.topup.TopupRepository.NewTopup;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

// Миграции на настоящем Postgres + бронь планировщика (FOR UPDATE SKIP LOCKED, claimed_until) и подсчёт лимита месяца
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({AutopaymentRepository.class, TopupRepository.class, SchedulerClaimsIT.Beans.class})
@TestPropertySource(properties = {
        "spring.datasource.hikari.schema=autopayments",
        "spring.liquibase.default-schema=autopayments",
        "spring.liquibase.liquibase-schema=autopayments"
})
class SchedulerClaimsIT {

    // Дата задолго до демо-данных: в пачку попадают только записи теста
    private static final LocalDate LONG_AGO = LocalDate.of(2000, 1, 1);
    private static final OffsetDateTime NOW = LONG_AGO.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    private static final UUID USER = UUID.randomUUID();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("create-schema.sql");

    @TestConfiguration
    static class Beans {

        @Bean
        AutopayProperties autopayProperties() {
            return TestProps.autopay();
        }

        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("Europe/Moscow"));
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    AutopaymentRepository autopayments;

    @Autowired
    TopupRepository topups;

    private UUID dueAutopayment() {
        UUID id = UUID.randomUUID();
        Target.Checked target = new Target.Checked(new Provider("yota", "MOBILE", "Yota", null, "#00AEEF", null),
                Map.of("phone", "9990000000"), "+7 999 000-00-00");
        autopayments.insert(id, USER, "Yota", target, new BigDecimal("300"), UUID.randomUUID(), null,
                Schedule.monthly(1, LONG_AGO), LONG_AGO.minusDays(1));
        return id;
    }

    private UUID byBalanceTopup() {
        UUID id = UUID.randomUUID();
        topups.insert(new NewTopup(id, USER, "Кошелёк", SourceKind.OWN, UUID.randomUUID(), "Карта", null, UUID.randomUUID(),
                "Кошелёк", TopupKind.BY_BALANCE, null, new BigDecimal("500"), new BigDecimal("1000"), null,
                new BigDecimal("3000"), null, null));
        return id;
    }

    @Test
    void claimedAutopaymentIsSkippedUntilClaimExpires() {
        UUID id = dueAutopayment();

        assertThat(autopayments.claimDue(LONG_AGO, NOW)).containsExactly(id);
        // Второй под в ту же минуту — запись занята
        assertThat(autopayments.claimDue(LONG_AGO, NOW.plusSeconds(1))).isEmpty();
        // Бронь истекла (первый под не довёл запуск) — запись снова в работе
        assertThat(autopayments.claimDue(LONG_AGO, NOW.plus(TestProps.CLAIM_FOR).plusSeconds(1))).containsExactly(id);
    }

    @Test
    void finishedAutopaymentMovesToNextDateAndReleasesClaim() {
        UUID id = dueAutopayment();
        autopayments.claimDue(LONG_AGO, NOW);
        Autopayment a = autopayments.get(id).orElseThrow();
        Autopayment.Run run = autopayments.claim(a, a.nextRunDate());

        autopayments.finish(a, run, RunStatus.SUCCESS, UUID.randomUUID(), BigDecimal.ZERO, "Оплачено", LONG_AGO.plusMonths(1));

        Autopayment after = autopayments.get(id).orElseThrow();
        assertThat(after.lastStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(after.nextRunDate()).isEqualTo(LONG_AGO.plusMonths(1));
        assertThat(autopayments.claimDue(LONG_AGO, NOW.plusSeconds(1))).isEmpty();
        // Тот же запуск на ту же дату второй раз не создаётся
        assertThat(autopayments.claim(a, a.nextRunDate()).status()).isEqualTo(RunStatus.SUCCESS);
    }

    @Test
    void monthLimitCountsSuccessfulAndUnfinishedRuns() {
        UUID id = byBalanceTopup();
        assertThat(topups.claimDue(LONG_AGO, NOW)).containsExactly(id);
        Topup t = topups.get(id).orElseThrow();

        Topup.Run ok = topups.claim(id, "b:1", new BigDecimal("500"), new BigDecimal("900"));
        topups.finish(t, ok, RunStatus.SUCCESS, UUID.randomUUID(), BigDecimal.ZERO, "Пополнено", null, NOW);
        Topup.Run failed = topups.claim(id, "b:2", new BigDecimal("700"), new BigDecimal("900"));
        topups.finish(t, failed, RunStatus.FAILED, null, null, "Отклонён", null, NOW);
        topups.claim(id, "b:3", new BigDecimal("200"), new BigDecimal("900"));

        Topup after = topups.get(id).orElseThrow();
        assertThat(after.monthUsed()).isEqualByComparingTo("700");
        assertThat(topups.pending(id)).isPresent();
        // finish снял бронь — запись снова доступна планировщику
        assertThat(topups.claimDue(LONG_AGO, NOW.plusSeconds(1))).containsExactly(id);
    }
}
