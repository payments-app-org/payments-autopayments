package com.paymentsapp.autopayments.client;

// Имена соседей: группы HTTP-клиентов (spring.http.serviceclient.*) и экземпляры Resilience4j
public final class Backends {

    public static final String ACCOUNT = "account";
    public static final String BILLER = "biller";
    public static final String PRICING = "pricing";
    public static final String PAYMENT = "payment";
    // Платёж повторяем только при ошибке соединения: остальное доведёт следующий проход с тем же ключом идемпотентности
    public static final String PAYMENT_CONNECT_ONLY = "payment-connect-only";

    private Backends() {
    }
}
