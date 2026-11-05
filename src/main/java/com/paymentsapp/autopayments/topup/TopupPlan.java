package com.paymentsapp.autopayments.topup;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Решение по балансу счёта зачисления: пополнять ли и на сколько. Без походов в сервисы — легко проверить тестом.
final class TopupPlan {

    // Копейки
    private static final int MONEY_SCALE = 2;

    enum Action {
        // Пополнить на amount
        TOP_UP,
        // Пополнять не нужно (или сумма меньше минимальной) — проверим на следующем проходе
        CHECK_NEXT_TICK,
        // Лимит месяца не даёт пополнить — подождём дольше
        LIMIT_REACHED
    }

    record Decision(Action action, BigDecimal amount) {
    }

    private TopupPlan() {
    }

    // По балансу — не пополняем, если сумма не влезает в лимит месяца; постоянный баланс — пополняем на остаток лимита
    static Decision decide(Topup t, BigDecimal balance, BigDecimal minAmount) {
        BigDecimal amount = null;
        if (t.kind() == TopupKind.BY_BALANCE && balance.compareTo(t.threshold()) <= 0) {
            amount = t.amount();
        } else if (t.kind() == TopupKind.CONSTANT_BALANCE && balance.compareTo(t.targetBalance()) < 0) {
            amount = t.targetBalance().subtract(balance).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (amount == null) {
            return new Decision(Action.CHECK_NEXT_TICK, null);
        }
        if (t.monthlyLimit() != null) {
            BigDecimal left = t.monthlyLimit().subtract(t.monthUsed());
            if (t.kind() == TopupKind.CONSTANT_BALANCE && amount.compareTo(left) > 0) {
                amount = left;
            }
            if (amount.compareTo(left) > 0 || left.compareTo(minAmount) < 0) {
                return new Decision(Action.LIMIT_REACHED, null);
            }
        }
        if (amount.compareTo(minAmount) < 0) {
            return new Decision(Action.CHECK_NEXT_TICK, null);
        }
        return new Decision(Action.TOP_UP, amount);
    }
}
