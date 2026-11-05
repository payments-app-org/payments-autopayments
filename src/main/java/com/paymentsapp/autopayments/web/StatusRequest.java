package com.paymentsapp.autopayments.web;

import com.paymentsapp.autopayments.common.Status;

// PATCH {"status": "PAUSED" | "ACTIVE"}
public record StatusRequest(Status status) {
}
