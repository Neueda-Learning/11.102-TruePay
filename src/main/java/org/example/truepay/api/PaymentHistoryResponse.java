package org.example.truepay.api;

import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentHistoryResponse(
		String transactionId,
		Instant date,
		PaymentMethod paymentMethod,
		String senderAccount,
		String receiverAccount,
		BigDecimal amount,
		String currency,
		PaymentStatus status,
		String failureReason
) {
}

