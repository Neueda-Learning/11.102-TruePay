package org.example.truepay.api;

import org.example.truepay.model.ErrorCode;
import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String paymentId,
        Long userId,
        Long sourceAccountId,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String message,
        String failureReason,
        ErrorCode errorCode,
        String errorMessage,
        String destinationUpiId,
        String destinationAccount,
        String destinationIfsc,
        String receiverName,
        String referenceRemark,
        Instant createdAt,
        Instant updatedAt
) {
}

