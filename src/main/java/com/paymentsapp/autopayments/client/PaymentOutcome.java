package com.paymentsapp.autopayments.client;

import java.math.BigDecimal;
import java.util.UUID;

// Итог платежа в payment для автоплатежа и автопополнения
public record PaymentOutcome(UUID paymentId, Result result, BigDecimal commission, String failureCode, String message) {

    // Статусы и код отказа payment (PaymentStatus, FailureCode); прочие статусы — платёж ещё проводится
    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String FAILED_STATUS = "FAILED";
    private static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    // REJECTED — payment не принял запрос (4xx): платёж не создан, причина в message
    public enum Result {
        SUCCESS,
        FAILED,
        REJECTED,
        IN_PROGRESS
    }

    static PaymentOutcome of(PaymentApi.PaymentResponse response) {
        Result result = SUCCESS_STATUS.equals(response.status()) ? Result.SUCCESS
                : FAILED_STATUS.equals(response.status()) ? Result.FAILED : Result.IN_PROGRESS;
        return new PaymentOutcome(response.paymentId(), result, response.commission(), response.failureCode(),
                response.message());
    }

    public static PaymentOutcome rejected(String message) {
        return new PaymentOutcome(null, Result.REJECTED, null, null, message);
    }

    public static PaymentOutcome success(UUID paymentId, BigDecimal commission) {
        return new PaymentOutcome(paymentId, Result.SUCCESS, commission, null, null);
    }

    public static PaymentOutcome inProgress(UUID paymentId) {
        return new PaymentOutcome(paymentId, Result.IN_PROGRESS, null, null, null);
    }

    public static PaymentOutcome failed(UUID paymentId, String message) {
        return new PaymentOutcome(paymentId, Result.FAILED, null, null, message);
    }

    public static PaymentOutcome notEnoughMoney(UUID paymentId, String message) {
        return new PaymentOutcome(paymentId, Result.FAILED, null, INSUFFICIENT_FUNDS, message);
    }

    public boolean insufficientFunds() {
        return result == Result.FAILED && INSUFFICIENT_FUNDS.equals(failureCode);
    }
}
