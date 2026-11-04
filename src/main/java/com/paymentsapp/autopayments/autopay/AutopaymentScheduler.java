package com.paymentsapp.autopayments.autopay;

import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Раз в interval ищет автоплатежи, у которых наступил день, и проводит их
@Component
public class AutopaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutopaymentScheduler.class);

    private final AutopaymentRepository repo;
    private final AutopaymentExecutor executor;
    private final AutopaymentService service;

    public AutopaymentScheduler(AutopaymentRepository repo, AutopaymentExecutor executor, AutopaymentService service) {
        this.repo = repo;
        this.executor = executor;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${autopay.interval}", initialDelayString = "10s")
    public void tick() {
        LocalDate today = service.today();
        for (UUID id : repo.due(today)) {
            try {
                executor.run(id, today);
            } catch (RuntimeException e) {
                log.warn("автоплатёж {}: запуск не удался: {}", id, e.getMessage());
            }
        }
    }
}
