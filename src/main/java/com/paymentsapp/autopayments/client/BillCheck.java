package com.paymentsapp.autopayments.client;

import java.util.List;

// Ответ biller на проверку платежа поставщику: поля по шаблону поставщика, display — как показать пользователю
public record BillCheck(String providerCode, String providerGroup, String recipientName, String purpose,
                        List<Field> fields) {

    public record Field(String key, String label, String value, String display) {
    }
}
