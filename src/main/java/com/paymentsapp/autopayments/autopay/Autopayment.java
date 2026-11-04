package com.paymentsapp.autopayments.autopay;

import com.paymentsapp.autopayments.template.Template.ProviderView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Autopayment(UUID id, UUID userId, String name, ProviderView provider, Map<String, String> fields, String target,
                          BigDecimal amount, UUID sourceAccountId, UUID backupAccountId, String scheduleKind,
                          Integer dayOfMonth, Integer periodDays, LocalDate startDate, LocalDate nextRunDate, String status,
                          LocalDate lastRunDate, String lastStatus, String lastMessage) {

    public Schedule schedule() {
        return new Schedule(scheduleKind, dayOfMonth, periodDays, startDate);
    }

    // Запуск: когда, чем закончился, какой платёж и с какого счёта
    public record Run(UUID id, LocalDate runDate, String status, UUID paymentId, UUID accountId, BigDecimal amount,
                      BigDecimal commission, String message, OffsetDateTime finishedAt) {
    }

    public record WithRuns(Autopayment autopayment, List<Run> runs) {
    }
}
