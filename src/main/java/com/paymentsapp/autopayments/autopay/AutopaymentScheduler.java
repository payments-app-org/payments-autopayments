package com.paymentsapp.autopayments.autopay;

import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Раз в interval ищет автоплатежи, у которых наступил день, и автопополнения, которым пора сработать
@Component
public class AutopaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutopaymentScheduler.class);

    private final AutopaymentRepository repo;
    private final AutopaymentExecutor executor;
    private final AutopaymentService service;
    private final com.paymentsapp.autopayments.topup.TopupRepository topups;
    private final com.paymentsapp.autopayments.topup.TopupExecutor topupExecutor;

    public AutopaymentScheduler(AutopaymentRepository repo, AutopaymentExecutor executor, AutopaymentService service,
                                com.paymentsapp.autopayments.topup.TopupRepository topups,
                                com.paymentsapp.autopayments.topup.TopupExecutor topupExecutor) {
        this.repo = repo;
        this.executor = executor;
        this.service = service;
        this.topups = topups;
        this.topupExecutor = topupExecutor;
    }

    // При нескольких подах тик выполняет один (ShedLock): автопополнение не сработает дважды
    @Scheduled(fixedDelayString = "${autopay.interval}", initialDelayString = "${autopay.initial-delay}")
    @SchedulerLock(name = "autopayments-tick")
    public void tick() {
        LocalDate today = service.today();
        for (UUID id : repo.due(today)) {
            try {
                executor.run(id, today);
            } catch (RuntimeException e) {
                log.warn("автоплатёж {}: запуск не удался: {}", id, e.getMessage());
            }
        }
        // Автопополнения: по дате и по балансу счёта зачисления
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        for (UUID id : topups.due(today, now)) {
            try {
                topupExecutor.run(id, today, now);
            } catch (RuntimeException e) {
                log.warn("автопополнение {}: запуск не удался: {}", id, e.getMessage());
            }
        }
    }
}
