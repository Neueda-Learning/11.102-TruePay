package org.example.truepay.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsMobileWhenNotExactlyTenDigits() {
        RegisterRequest request = new RegisterRequest(
                "Alex Sharma",
                "alex@example.com",
                "12345",
                "1234",
                "secret123"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v ->
                "mobile".equals(v.getPropertyPath().toString())
                        && "Mobile number must be exactly 10 digits".equals(v.getMessage())));
    }

    @Test
    void acceptsMobileWhenExactlyTenDigits() {
        RegisterRequest request = new RegisterRequest(
                "Alex Sharma",
                "alex@example.com",
                "9876543210",
                "1234",
                "secret123"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertFalse(violations.stream().anyMatch(v -> "mobile".equals(v.getPropertyPath().toString())));
    }
}

