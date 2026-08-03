package org.example.truepay.api;

import org.example.truepay.model.ErrorCode;

import java.time.Instant;

public record ApiErrorResponse(
        ErrorCode code,
        String message,
        Instant timestamp
) {
}

