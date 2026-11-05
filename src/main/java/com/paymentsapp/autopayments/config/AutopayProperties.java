package com.paymentsapp.autopayments.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Настройки autopay.* (application.yml)
@ConfigurationProperties("autopay")
public record AutopayProperties(
        // Как часто планировщик ищет, что пора провести, и пауза после старта
        Duration interval,
        Duration initialDelay,
        // Сколько записей под забирает за проход, сколько проводит одновременно и на сколько бронирует их за собой
        int batchSize,
        int parallelism,
        Duration claimFor,
        // Минимум суммы автоплатежа, пополнения и лимита в месяц
        BigDecimal minAmount,
        // «Каждые N дней» — не больше maxPeriodDays; первый платёж — не дальше maxStartAhead от сегодня
        int maxPeriodDays,
        Period maxStartAhead,
        // Длина названий — по колонкам таблиц
        int nameMax,
        int templateNameMax,
        // Сколько последних запусков показываем в карточке
        int runsShown,
        Topup topup
) {

    // Пауза до следующей проверки баланса: после пополнения — дать балансу обновиться, после отказа — не долбить источник
    public record Topup(Duration afterSuccess, Duration afterFailure) {
    }
}
