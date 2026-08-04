package org.example.truepay.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record BankPaymentRequest(
        @NotNull Long sourceAccountId,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be ISO-4217 format") String currency,
        Long beneficiaryId,
        String receiverName,
        String destinationAccount,
        String destinationIfsc,
        String reference,
        @Pattern(regexp = "^\\d{4}$", message = "App PIN must be 4 digits") String appPin,
        @Pattern(regexp = "^\\d{6}$", message = "Bank PIN must be 6 digits") String bankPin
) {
}

