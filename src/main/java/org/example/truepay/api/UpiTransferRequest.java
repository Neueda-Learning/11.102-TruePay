package org.example.truepay.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UpiTransferRequest(
		@NotBlank String sourceAccount,
		@NotBlank String receiverType,
		@NotBlank @Pattern(regexp = "^(\\d{10}|[a-zA-Z0-9._-]+@[a-zA-Z][a-zA-Z0-9]{2,})$", message = "Receiver must be a valid UPI ID (e.g. name@bank) or 10-digit mobile number") String receiver,
		@NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
		@NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be ISO-4217 format") String currency,
		@NotBlank @Pattern(regexp = "^\\d{6}$", message = "Bank PIN must be 6 digits") String bankPin
) {
}

