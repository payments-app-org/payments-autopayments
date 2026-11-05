package com.paymentsapp.autopayments.client;

import java.math.BigDecimal;

// Котировка pricing: комиссия и подписанный токен для payment
public record Quote(BigDecimal commission, BigDecimal total, String quoteToken) {
}
