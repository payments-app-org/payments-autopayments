package com.paymentsapp.autopayments.topup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Автопополнение своего счёта. monthUsed — сколько уже пополнено в этом месяце (для лимита).
public record Topup(UUID id, UUID userId, String name, String sourceKind, UUID sourceAccountId, String sourceTitle,
                    String sourceBankId, UUID destinationAccountId, String destinationTitle, String kind, Integer dayOfMonth,
                    BigDecimal amount, BigDecimal threshold, BigDecimal targetBalance, BigDecimal monthlyLimit,
                    LocalDate nextRunDate, OffsetDateTime nextCheckAt, String status, OffsetDateTime lastRunAt,
                    String lastStatus, String lastMessage, BigDecimal monthUsed) {

    public record Run(UUID id, String triggerKey, String status, BigDecimal amount, BigDecimal balanceBefore, UUID paymentId,
                      BigDecimal commission, String message, OffsetDateTime createdAt) {
    }

    public record WithRuns(Topup topup, List<Run> runs) {
    }
}
