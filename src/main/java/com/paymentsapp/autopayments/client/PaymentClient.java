package com.paymentsapp.autopayments.client;

import static com.paymentsapp.autopayments.error.AutopaymentsError.PAYMENT_REJECTED;
import static com.paymentsapp.autopayments.error.AutopaymentsError.TRANSFER_REJECTED;

import com.paymentsapp.autopayments.client.PaymentApi.PaymentRequest;
import com.paymentsapp.autopayments.error.AutopaymentsError;
import com.paymentsapp.platform.resilience.ResilientCalls;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

// payment: платёж с ключом идемпотентности. Отказ проверки (4xx) — тоже результат: платёж не создан (REJECTED).
// Сбой связи — RestClientException: запуск остаётся незавершённым и повторится с тем же ключом.
@Component
public class PaymentClient {

    private final PaymentApi api;
    private final ResilientCalls calls;
    private final JsonMapper json;

    public PaymentClient(PaymentApi api, ResilientCalls calls, JsonMapper json) {
        this.api = api;
        this.calls = calls;
        this.json = json;
    }

    public PaymentOutcome payBill(UUID userId, String idempotencyKey, UUID accountId, String providerCode,
                                  Map<String, String> fields, BigDecimal amount, String quoteToken) {
        return pay(userId, idempotencyKey,
                new PaymentRequest(OperationType.BILL, accountId, amount, quoteToken, providerCode, fields, null),
                PAYMENT_REJECTED);
    }

    public PaymentOutcome payOwn(UUID userId, String idempotencyKey, UUID sourceId, UUID destinationId, BigDecimal amount,
                                 String quoteToken) {
        return pay(userId, idempotencyKey,
                new PaymentRequest(OperationType.OWN_TRANSFER, sourceId, amount, quoteToken, null, null, destinationId),
                TRANSFER_REJECTED);
    }

    private PaymentOutcome pay(UUID userId, String idempotencyKey, PaymentRequest request, AutopaymentsError onReject) {
        try {
            return PaymentOutcome.of(calls.call(Backends.PAYMENT, Backends.PAYMENT_CONNECT_ONLY,
                    () -> api.create(userId, idempotencyKey, request)));
        } catch (HttpClientErrorException e) {
            return PaymentOutcome.rejected(Rejections.detail(e, json, onReject));
        }
    }
}
