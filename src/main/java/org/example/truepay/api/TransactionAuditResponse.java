package org.example.truepay.api;

import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionAuditResponse(
        UUID paymentId,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        String receiver,
        PaymentStatus status,
        String triggeredBy,
        Instant changedAt,
        String notes,
        String idempotencyKey,
        String referenceRemark
) {
}
