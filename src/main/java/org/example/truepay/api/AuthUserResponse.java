package org.example.truepay.api;

public record AuthUserResponse(
        Long id,
        String fullName,
        String email,
        String mobile
) {
}

