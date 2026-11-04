package com.paymentsapp.autopayments.client;

import com.paymentsapp.autopayments.web.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// REST-клиенты: account (счета пользователя), biller (проверка платежа поставщику), pricing (котировка), payment (платёж)
@Component
public class Clients {

    private final RestClient account;
    private final RestClient biller;
    private final RestClient pricing;
    private final RestClient payment;
    private final JsonMapper json;

    public Clients(RestClient.Builder builder, JsonMapper json,
                   @Value("${clients.account.url}") String accountUrl, @Value("${clients.biller.url}") String billerUrl,
                   @Value("${clients.pricing.url}") String pricingUrl, @Value("${clients.payment.url}") String paymentUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.account = builder.clone().requestFactory(factory).baseUrl(accountUrl).build();
        this.biller = builder.clone().requestFactory(factory).baseUrl(billerUrl).build();
        this.pricing = builder.clone().requestFactory(factory).baseUrl(pricingUrl).build();
        this.payment = builder.clone().requestFactory(factory).baseUrl(paymentUrl).build();
        this.json = json;
    }

    public List<Account> accounts(UUID userId) {
        return account.get().uri("/api/users/{id}/accounts", userId).retrieve().body(new org.springframework.core.ParameterizedTypeReference<List<Account>>() { });
    }

    // Проверка платежа поставщику теми же правилами, что и при обычной оплате. Отказ biller — его текст пользователю.
    public BillCheck checkBill(String providerCode, Map<String, String> fields, BigDecimal amount) {
        try {
            return biller.post().uri("/api/biller/bills/check")
                    .body(Map.of("providerCode", providerCode, "fields", fields, "amount", amount))
                    .retrieve().body(BillCheck.class);
        } catch (HttpClientErrorException e) {
            throw ApiException.badRequest(detail(e, "Проверьте данные платежа"));
        }
    }

    public Provider provider(String code) {
        try {
            JsonNode d = biller.get().uri("/api/biller/providers/{code}", code).retrieve().body(JsonNode.class);
            return json.treeToValue(d.path("provider"), Provider.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.badRequest("Поставщик не найден");
        }
    }

    // Котировка на платёж поставщику: комиссия и подписанный токен для payment
    public Quote quote(UUID userId, UUID accountId, String providerCode, BigDecimal amount) {
        try {
            return pricing.post().uri("/api/pricing/commission")
                    .body(Map.of("userId", userId, "sourceAccountId", accountId, "operationType", "BILL",
                            "amount", amount, "providerCode", providerCode))
                    .retrieve().body(Quote.class);
        } catch (HttpClientErrorException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, detail(e, "Не удалось рассчитать комиссию"));
        }
    }

    // Платёж в payment. Ответ с ошибкой проверки (4xx) — тоже результат: платёж не создан, причина в message.
    public PaymentResult pay(UUID userId, String idempotencyKey, UUID accountId, String providerCode,
                             Map<String, String> fields, BigDecimal amount, String quoteToken) {
        try {
            return payment.post().uri("/api/payments")
                    .header("X-User-Id", userId.toString()).header("X-Idempotency-Key", idempotencyKey)
                    .body(Map.of("type", "BILL", "sourceAccountId", accountId, "providerCode", providerCode,
                            "recipientFields", fields, "amount", amount, "quoteToken", quoteToken))
                    .retrieve().body(PaymentResult.class);
        } catch (HttpClientErrorException e) {
            return new PaymentResult(null, "REJECTED", null, null, detail(e, "Платёж отклонён"));
        }
    }

    // «Ваш счёт в другом банке»: счёт-источник пополнения по СБП в account (находит или заводит)
    public Account sbpSource(UUID userId, String bankId, String bankName) {
        try {
            return account.post().uri("/api/users/{id}/sbp-sources", userId)
                    .body(Map.of("bankId", bankId, "bankName", bankName)).retrieve().body(Account.class);
        } catch (HttpClientErrorException e) {
            throw ApiException.badRequest(detail(e, "Выберите банк"));
        }
    }

    // Котировка перевода между своими счетами (автопополнение)
    public Quote quoteOwn(UUID userId, UUID sourceId, UUID destinationId, BigDecimal amount) {
        try {
            return pricing.post().uri("/api/pricing/commission")
                    .body(Map.of("userId", userId, "sourceAccountId", sourceId, "operationType", "OWN_TRANSFER",
                            "amount", amount, "destinationAccountId", destinationId))
                    .retrieve().body(Quote.class);
        } catch (HttpClientErrorException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, detail(e, "Такое пополнение недоступно"));
        }
    }

    public PaymentResult payOwn(UUID userId, String idempotencyKey, UUID sourceId, UUID destinationId, BigDecimal amount,
                                String quoteToken) {
        try {
            return payment.post().uri("/api/payments")
                    .header("X-User-Id", userId.toString()).header("X-Idempotency-Key", idempotencyKey)
                    .body(Map.of("type", "OWN_TRANSFER", "sourceAccountId", sourceId, "destinationAccountId", destinationId,
                            "amount", amount, "quoteToken", quoteToken))
                    .retrieve().body(PaymentResult.class);
        } catch (HttpClientErrorException e) {
            return new PaymentResult(null, "REJECTED", null, null, detail(e, "Перевод отклонён"));
        }
    }

    private String detail(HttpClientErrorException e, String fallback) {
        try {
            return json.readTree(e.getResponseBodyAsString()).path("detail").asString(fallback);
        } catch (RuntimeException parse) {
            return fallback;
        }
    }

    public record Account(UUID id, String type, String status, String title, String cardLast4, BigDecimal available) {

        // Кошелёк или карта нашего банка
        public boolean usable() {
            return "ACTIVE".equals(status) && ("WALLET".equals(type) || "CARD".equals(type));
        }

        // «Зарплатная WEEKEND ·· 3752»
        public String displayTitle() {
            return title + (cardLast4 == null ? "" : " ·· " + cardLast4);
        }
    }

    public record BillCheck(String providerCode, String providerGroup, String recipientName, String purpose,
                            List<BillField> fields) {
    }

    public record BillField(String key, String label, String value, String display) {
    }

    public record Provider(String code, String groupCode, String name, String domain, String color, String countryCode) {
    }

    public record Quote(BigDecimal commission, BigDecimal total, String quoteToken) {
    }

    // status: SUCCESS / FAILED / в процессе; REJECTED — payment не принял запрос
    public record PaymentResult(UUID paymentId, String status, BigDecimal commission, String failureCode, String message) {
    }
}
