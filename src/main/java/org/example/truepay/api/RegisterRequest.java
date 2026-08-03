package org.example.truepay.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank String mobile,
        @Pattern(regexp = "^\\d{4}$", message = "App PIN must be 4 digits") String appPin,
        @NotBlank String password
) {
}

