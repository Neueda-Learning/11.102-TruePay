package org.example.truepay.api;

public record ProfileResponse(
        Long id,
        String fullName,
        String email,
        String mobile
) {
}

