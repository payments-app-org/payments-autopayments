package com.paymentsapp.autopayments.client;

import java.math.BigDecimal;
import java.util.UUID;

// Счёт пользователя из account
public record Account(UUID id, AccountType type, AccountStatus status, String title, String cardLast4, BigDecimal available) {

    private static final String CARD_SUFFIX = " ·· ";

    // Кошелёк или карта нашего банка
    public boolean usable() {
        return status == AccountStatus.ACTIVE && (type == AccountType.WALLET || type == AccountType.CARD);
    }

    // «Зарплатная WEEKEND ·· 3752»
    public String displayTitle() {
        return cardLast4 == null ? title : title + CARD_SUFFIX + cardLast4;
    }

    public enum AccountType {
        WALLET,
        CARD,
        EXTERNAL_CARD,
        EXTERNAL_SBP
    }

    public enum AccountStatus {
        ACTIVE,
        BLOCKED,
        CLOSED
    }
}
