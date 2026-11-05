package com.paymentsapp.autopayments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Шаблоны платежей и автоплатежи: хранит их и по расписанию сам создаёт платежи в payment
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AutopaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutopaymentsApplication.class, args);
    }
}
