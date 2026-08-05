package org.example.truepay.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record BankAccountRequest(
        @NotBlank String bankName,
        @NotBlank @Pattern(regexp = "^\\d{8,12}$", message = "Account number must be 8-12 digits") String accountNumber,
        @NotBlank String ifscCode,
        @NotBlank String accountType,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Bank PIN must be 6 digits") String bankPin,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal openingBalance
) {
}

