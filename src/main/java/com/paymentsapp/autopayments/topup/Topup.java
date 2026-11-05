package com.paymentsapp.autopayments.topup;

import com.paymentsapp.autopayments.common.RunStatus;
import com.paymentsapp.autopayments.common.Status;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Автопополнение своего счёта. monthUsed — сколько уже пополнено в этом месяце (для лимита).
public record Topup(UUID id, UUID userId, String name, SourceKind sourceKind, UUID sourceAccountId, String sourceTitle,
                    String sourceBankId, UUID destinationAccountId, String destinationTitle, TopupKind kind, Integer dayOfMonth,
                    BigDecimal amount, BigDecimal threshold, BigDecimal targetBalance, BigDecimal monthlyLimit,
                    LocalDate nextRunDate, OffsetDateTime nextCheckAt, Status status, OffsetDateTime lastRunAt,
                    RunStatus lastStatus, String lastMessage, BigDecimal monthUsed) {

    public record Run(UUID id, String triggerKey, RunStatus status, BigDecimal amount, BigDecimal balanceBefore,
                      UUID paymentId, BigDecimal commission, String message, OffsetDateTime createdAt) {
    }

    public record WithRuns(Topup topup, List<Run> runs) {
    }
}
