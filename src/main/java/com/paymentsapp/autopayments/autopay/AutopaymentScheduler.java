package com.paymentsapp.autopayments.autopay;

import com.paymentsapp.autopayments.config.AutopayProperties;
import com.paymentsapp.autopayments.topup.TopupExecutor;
import com.paymentsapp.autopayments.topup.TopupRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Раз в interval: автоплатежи, у которых наступил день, и автопополнения, которым пора сработать.
// Каждый под забирает свою пачку (бронь в БД, FOR UPDATE SKIP LOCKED) и проводит её параллельно,
// не больше autopay.parallelism одновременно. Несколько подов делят работу, а не дублируют.
@Component
public class AutopaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutopaymentScheduler.class);
    private static final String AUTOPAYMENT = "автоплатёж";
    private static final String TOPUP = "автопополнение";

    private final AutopaymentRepository autopayments;
    private final AutopaymentExecutor executor;
    private final TopupRepository topups;
    private final TopupExecutor topupExecutor;
    private final AutopayProperties props;
    private final Clock clock;

    public AutopaymentScheduler(AutopaymentRepository autopayments, AutopaymentExecutor executor, TopupRepository topups,
                                TopupExecutor topupExecutor, AutopayProperties props, Clock clock) {
        this.autopayments = autopayments;
        this.executor = executor;
        this.topups = topups;
        this.topupExecutor = topupExecutor;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${autopay.interval}", initialDelayString = "${autopay.initial-delay}")
    public void tick() {
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);
        runAll(autopayments.claimDue(today, now), id -> executor.run(id, today), AUTOPAYMENT);
        runAll(topups.claimDue(today, now), id -> topupExecutor.run(id, today, now), TOPUP);
    }

    private void runAll(List<UUID> ids, Consumer<UUID> task, String what) {
        if (ids.isEmpty()) {
            return;
        }
        // close() дожидается всех задач пачки
        try (ExecutorService pool = Executors.newFixedThreadPool(props.parallelism(), Thread.ofVirtual().factory())) {
            for (UUID id : ids) {
                pool.submit(() -> {
                    try {
                        task.accept(id);
                    } catch (RuntimeException e) {
                        log.warn("{} {}: запуск не удался: {}", what, id, e.getMessage());
                    }
                });
            }
        }
    }
}
