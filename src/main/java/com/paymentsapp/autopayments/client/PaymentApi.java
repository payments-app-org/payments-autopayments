package com.paymentsapp.autopayments.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/payments")
public interface PaymentApi {

    String USER_HEADER = "X-User-Id";
    String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    // 200 — платёж завершён, 202 — ещё проводится; тело одно и то же
    @PostExchange
    PaymentResponse create(@RequestHeader(USER_HEADER) UUID userId, @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                           @RequestBody PaymentRequest request);

    record PaymentRequest(OperationType type, UUID sourceAccountId, BigDecimal amount, String quoteToken,
                          String providerCode, Map<String, String> recipientFields, UUID destinationAccountId) {
    }

    record PaymentResponse(UUID paymentId, String status, BigDecimal commission, String failureCode, String message) {
    }
}
