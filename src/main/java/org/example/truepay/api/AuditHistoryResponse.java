package org.example.truepay.api;

import java.time.Instant;

public record AuditHistoryResponse(
		Long id,
		Long userId,
		String transactionId,
		String action,
		String description,
		Instant timestamp
) {
}

