package com.paymentsapp.autopayments.common;

// Итог запуска для пользователя. Отказ — текстом от payment или pricing.
public enum RunMessage {
    PAID("Оплачено"),
    PAID_FROM_BACKUP("Оплачено с запасного счёта"),
    PAYMENT_PROCESSING("Платёж обрабатывается"),
    TOPPED_UP("Пополнено"),
    TRANSFER_PROCESSING("Перевод обрабатывается");

    private final String text;

    RunMessage(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
