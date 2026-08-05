package org.example.truepay.api;

import java.math.BigDecimal;

public record PaymentLimitResponse(
        Long userId,
        boolean dailyEnabled,
        BigDecimal dailyLimit,
        boolean monthlyEnabled,
        BigDecimal monthlyLimit,
        boolean perTransactionEnabled,
        BigDecimal perTransactionLimit
) {
}

