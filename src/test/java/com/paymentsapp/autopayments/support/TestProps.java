package com.paymentsapp.autopayments.support;

import com.paymentsapp.autopayments.config.AutopayProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;

// Настройки autopay.* как в application.yml
public final class TestProps {

    public static final Duration CLAIM_FOR = Duration.ofMinutes(2);
    public static final BigDecimal MIN_AMOUNT = BigDecimal.TEN;

    private TestProps() {
    }

    public static AutopayProperties autopay() {
        return new AutopayProperties(Duration.ofSeconds(30), Duration.ofSeconds(10), 50, 8, CLAIM_FOR, MIN_AMOUNT, 180,
                Period.ofYears(1), 60, 40, 20,
                new AutopayProperties.Topup(Duration.ofMinutes(1), Duration.ofMinutes(30)));
    }
}
