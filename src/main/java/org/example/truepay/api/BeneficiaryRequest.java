package org.example.truepay.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BeneficiaryRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^\\d{8,12}$", message = "Account number must be 8-12 digits") String accountNumber,
        @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Receiver IFSC format is invalid") String ifscCode
) {
}

