package com.paymentsapp.autopayments.topup;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentsapp.autopayments.common.Status;
import com.paymentsapp.autopayments.topup.TopupPlan.Action;
import com.paymentsapp.autopayments.topup.TopupPlan.Decision;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopupPlanTest {

    private static final BigDecimal MIN = BigDecimal.TEN;

    private static Topup byBalance(String threshold, String amount, String limit, String used) {
        return topup(TopupKind.BY_BALANCE, new BigDecimal(threshold), new BigDecimal(amount), null, limit, used);
    }

    private static Topup constant(String target, String limit, String used) {
        return topup(TopupKind.CONSTANT_BALANCE, null, null, new BigDecimal(target), limit, used);
    }

    private static Topup topup(TopupKind kind, BigDecimal threshold, BigDecimal amount, BigDecimal target, String limit,
                               String used) {
        return new Topup(UUID.randomUUID(), UUID.randomUUID(), "Кошелёк", SourceKind.OWN, UUID.randomUUID(), "Карта", null,
                UUID.randomUUID(), "Кошелёк", kind, null, amount, threshold, target,
                limit == null ? null : new BigDecimal(limit), null, null, Status.ACTIVE, null, null, null,
                new BigDecimal(used));
    }

    @Test
    void byBalanceTopsUpWhenBalanceFallsToThreshold() {
        Decision d = TopupPlan.decide(byBalance("1000", "500", null, "0"), new BigDecimal("1000"), MIN);
        assertThat(d.action()).isEqualTo(Action.TOP_UP);
        assertThat(d.amount()).isEqualByComparingTo("500");

        assertThat(TopupPlan.decide(byBalance("1000", "500", null, "0"), new BigDecimal("1000.01"), MIN).action())
                .isEqualTo(Action.CHECK_NEXT_TICK);
    }

    @Test
    void byBalanceWaitsWhenAmountDoesNotFitMonthlyLimit() {
        // Лимит 1 000, пополнено 700: 500 не влезает — ждём, частями не пополняем
        Decision d = TopupPlan.decide(byBalance("1000", "500", "1000", "700"), new BigDecimal("100"), MIN);
        assertThat(d.action()).isEqualTo(Action.LIMIT_REACHED);
    }

    @Test
    void constantBalanceTopsUpToTargetCappedByLimit() {
        Decision full = TopupPlan.decide(constant("5000", null, "0"), new BigDecimal("3200.555"), MIN);
        assertThat(full.amount()).isEqualByComparingTo("1799.45");

        // До цели 4 000, а в лимите осталось 1 500 — пополняем на остаток лимита
        Decision capped = TopupPlan.decide(constant("5000", "3000", "1500"), new BigDecimal("1000"), MIN);
        assertThat(capped.action()).isEqualTo(Action.TOP_UP);
        assertThat(capped.amount()).isEqualByComparingTo("1500");

        // В лимите меньше минимума — ждём
        assertThat(TopupPlan.decide(constant("5000", "3000", "2995"), new BigDecimal("1000"), MIN).action())
                .isEqualTo(Action.LIMIT_REACHED);
    }

    @Test
    void tinyShortfallIsNotWorthTransfer() {
        assertThat(TopupPlan.decide(constant("5000", null, "0"), new BigDecimal("4995"), MIN).action())
                .isEqualTo(Action.CHECK_NEXT_TICK);
    }
}
