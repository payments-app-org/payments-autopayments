package com.paymentsapp.autopayments.client;

import static com.paymentsapp.autopayments.error.AutopaymentsError.QUOTE_FAILED;
import static com.paymentsapp.autopayments.error.AutopaymentsError.TOPUP_NOT_AVAILABLE;

import com.paymentsapp.autopayments.client.PricingApi.CommissionRequest;
import com.paymentsapp.autopayments.error.AutopaymentsError;
import com.paymentsapp.platform.resilience.ResilientCalls;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

// pricing: котировка на платёж поставщику и на перевод между своими счетами. Отказ pricing — DomainException его текстом.
@Component
public class PricingClient {

    private final PricingApi api;
    private final ResilientCalls calls;
    private final JsonMapper json;

    public PricingClient(PricingApi api, ResilientCalls calls, JsonMapper json) {
        this.api = api;
        this.calls = calls;
        this.json = json;
    }

    public Quote billQuote(UUID userId, UUID accountId, String providerCode, BigDecimal amount) {
        return quote(new CommissionRequest(userId, accountId, OperationType.BILL, amount, providerCode, null), QUOTE_FAILED);
    }

    public Quote ownTransferQuote(UUID userId, UUID sourceId, UUID destinationId, BigDecimal amount) {
        return quote(new CommissionRequest(userId, sourceId, OperationType.OWN_TRANSFER, amount, null, destinationId),
                TOPUP_NOT_AVAILABLE);
    }

    private Quote quote(CommissionRequest request, AutopaymentsError onReject) {
        try {
            return calls.call(Backends.PRICING, () -> api.commission(request));
        } catch (HttpClientErrorException e) {
            throw Rejections.of(onReject, e, json);
        }
    }
}
