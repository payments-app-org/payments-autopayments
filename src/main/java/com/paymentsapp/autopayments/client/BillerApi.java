package com.paymentsapp.autopayments.client;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/biller")
public interface BillerApi {

    @PostExchange("/bills/check")
    BillCheck check(@RequestBody BillCheckRequest request);

    @GetExchange("/providers/{code}")
    ProviderDetails provider(@PathVariable String code);

    record BillCheckRequest(String providerCode, Map<String, String> fields, BigDecimal amount) {
    }

    record ProviderDetails(Provider provider) {
    }
}
