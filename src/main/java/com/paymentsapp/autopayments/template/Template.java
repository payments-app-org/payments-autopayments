package com.paymentsapp.autopayments.template;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

// Шаблон платежа: открываешь — форма поставщика уже заполнена, остаётся нажать «Оплатить»
public record Template(UUID id, String name, ProviderView provider, Map<String, String> fields, String target,
                       BigDecimal amount, UUID sourceAccountId) {

    public record ProviderView(String code, String groupCode, String name, String domain, String color, String countryCode) {
    }
}
