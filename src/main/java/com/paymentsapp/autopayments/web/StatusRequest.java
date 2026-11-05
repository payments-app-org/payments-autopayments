package com.paymentsapp.autopayments.web;

import com.paymentsapp.autopayments.common.Status;
import jakarta.validation.constraints.NotNull;

// PATCH {"status": "PAUSED" | "ACTIVE"}
public record StatusRequest(@NotNull Status status) {
}
