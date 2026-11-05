package com.paymentsapp.autopayments.client;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/pricing")
public interface PricingApi {

    @PostExchange("/commission")
    Quote commission(@RequestBody CommissionRequest request);

    // providerCode — для оплаты поставщику, destinationAccountId — для перевода между своими счетами
    record CommissionRequest(UUID userId, UUID sourceAccountId, OperationType operationType, BigDecimal amount,
                             String providerCode, UUID destinationAccountId) {
    }
}
