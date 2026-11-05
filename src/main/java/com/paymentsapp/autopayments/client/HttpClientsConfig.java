package com.paymentsapp.autopayments.client;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = Backends.ACCOUNT, types = AccountApi.class)
@ImportHttpServices(group = Backends.BILLER, types = BillerApi.class)
@ImportHttpServices(group = Backends.PRICING, types = PricingApi.class)
@ImportHttpServices(group = Backends.PAYMENT, types = PaymentApi.class)
class HttpClientsConfig {
}
