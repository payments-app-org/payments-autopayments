package com.paymentsapp.autopayments.web;

import com.paymentsapp.autopayments.error.AutopaymentsError;
import com.paymentsapp.platform.error.Problems;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

// Отказы домена (DomainException) — обработчик платформы. Здесь — недоступный сосед: тайм-аут, 5xx, разомкнутый circuit breaker
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(RestClientException.class)
    ProblemDetail unavailable(RestClientException e) {
        return Problems.of(AutopaymentsError.SERVICE_UNAVAILABLE, AutopaymentsError.SERVICE_UNAVAILABLE.template());
    }
}
