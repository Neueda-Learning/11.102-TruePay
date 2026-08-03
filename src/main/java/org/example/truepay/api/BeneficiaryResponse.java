package org.example.truepay.api;

public record BeneficiaryResponse(
        Long id,
        String name,
        String accountNumber,
        String ifscCode
) {
}

