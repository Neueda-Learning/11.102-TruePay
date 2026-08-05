package org.example.truepay.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpiPaymentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private UpiPaymentRequest valid() {
        return new UpiPaymentRequest(
                1L,
                new BigDecimal("250.00"),
                "INR",
                "merchant@upi",
                "upi-20260805-001",
                "123456"
        );
    }

    private boolean hasViolationOn(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream().anyMatch(v -> field.equals(v.getPropertyPath().toString()));
    }

    @Test
    void acceptsCompletelyValidRequest() {
        Set<ConstraintViolation<UpiPaymentRequest>> violations = validator.validate(valid());
        assertTrue(violations.isEmpty(), "A valid UpiPaymentRequest should have no violations");
    }

    @Test
    void rejectsBlankDestinationUpiId() {
        UpiPaymentRequest request = new UpiPaymentRequest(
                1L,
                new BigDecimal("250.00"),
                "INR",
                "",
                "upi-20260805-001",
                "123456"
        );

        Set<ConstraintViolation<UpiPaymentRequest>> violations = validator.validate(request);
        assertTrue(hasViolationOn(violations, "destinationUpiId"));
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        UpiPaymentRequest request = new UpiPaymentRequest(
                1L,
                new BigDecimal("250.00"),
                "INR",
                "merchant@upi",
                "",
                "123456"
        );

        Set<ConstraintViolation<UpiPaymentRequest>> violations = validator.validate(request);
        assertTrue(hasViolationOn(violations, "idempotencyKey"));
    }

    @Test
    void rejectsMalformedBankPin() {
        UpiPaymentRequest request = new UpiPaymentRequest(
                1L,
                new BigDecimal("250.00"),
                "INR",
                "merchant@upi",
                "upi-20260805-001",
                "12A45"
        );

        Set<ConstraintViolation<UpiPaymentRequest>> violations = validator.validate(request);
        assertTrue(hasViolationOn(violations, "bankPin"));
    }

    @Test
    void rejectsLowercaseCurrency() {
        UpiPaymentRequest request = new UpiPaymentRequest(
                1L,
                new BigDecimal("250.00"),
                "inr",
                "merchant@upi",
                "upi-20260805-001",
                "123456"
        );

        Set<ConstraintViolation<UpiPaymentRequest>> violations = validator.validate(request);
        assertTrue(hasViolationOn(violations, "currency"));
    }

    @Test
    void doesNotRequireAppPinInRequestContract() {
        Set<ConstraintViolation<UpiPaymentRequest>> violations = validator.validate(valid());
        assertFalse(hasViolationOn(violations, "appPin"), "UPI flow should not require appPin");
    }
}

