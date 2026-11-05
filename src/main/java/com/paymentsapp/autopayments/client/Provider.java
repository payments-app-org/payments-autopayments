package com.paymentsapp.autopayments.client;

// Поставщик из biller: для витрины шаблона и автоплатежа
public record Provider(String code, String groupCode, String name, String domain, String color, String countryCode) {
}
