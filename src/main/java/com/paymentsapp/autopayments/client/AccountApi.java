package com.paymentsapp.autopayments.client;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface AccountApi {

    @GetExchange("/api/users/{userId}/accounts")
    List<Account> accounts(@PathVariable UUID userId);

    // «Ваш счёт в другом банке» — источник пополнения по СБП: находит или заводит
    @PostExchange("/api/users/{userId}/sbp-sources")
    Account sbpSource(@PathVariable UUID userId, @RequestBody SbpSourceRequest request);

    record SbpSourceRequest(String bankId, String bankName) {
    }
}
