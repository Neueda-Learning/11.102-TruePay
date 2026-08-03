package org.example.truepay.api;

import org.example.truepay.model.PaymentStatus;

import java.time.Instant;

public record StatusHistoryResponse(
        PaymentStatus status,
        String triggeredBy,
        Instant changedAt,
        String notes
) {
}

