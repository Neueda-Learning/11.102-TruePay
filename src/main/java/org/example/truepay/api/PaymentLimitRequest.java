package org.example.truepay.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentLimitRequest(
        @NotNull Boolean dailyEnabled,
        @DecimalMin(value = "0.01", inclusive = true, message = "Daily limit must be greater than zero") BigDecimal dailyLimit,
        @NotNull Boolean monthlyEnabled,
        @DecimalMin(value = "0.01", inclusive = true, message = "Monthly limit must be greater than zero") BigDecimal monthlyLimit,
        @NotNull Boolean perTransactionEnabled,
        @DecimalMin(value = "0.01", inclusive = true, message = "Per-transaction limit must be greater than zero") BigDecimal perTransactionLimit
) {
}

