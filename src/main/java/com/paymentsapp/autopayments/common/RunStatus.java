package com.paymentsapp.autopayments.common;

// Запуск автоплатежа или автопополнения. PENDING — не завершён (сбой связи): доведём тем же ключом идемпотентности.
// PROCESSING — платёж ещё проводится в payment, его доведёт payment.
public enum RunStatus {
    PENDING,
    SUCCESS,
    FAILED,
    PROCESSING
}
