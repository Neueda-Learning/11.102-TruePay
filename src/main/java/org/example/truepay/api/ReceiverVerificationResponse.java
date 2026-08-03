package org.example.truepay.api;

public record ReceiverVerificationResponse(
        boolean internalAccount,
        String receiverName,
        String message
) {
}

