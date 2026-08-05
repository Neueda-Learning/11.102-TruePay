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

class AccountNumberValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsBankAccountWhenAccountNumberTooShort() {
        BankAccountRequest request = new BankAccountRequest(
                "John Doe",
                "HDFC Bank",
                "1234567",  // 7 digits - too short
                "HDFC0001234",
                "SAVINGS",
                "123456",
                BigDecimal.valueOf(10000)
        );

        Set<ConstraintViolation<BankAccountRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v ->
                "accountNumber".equals(v.getPropertyPath().toString())
                        && "Account number must be 8-12 digits".equals(v.getMessage())));
    }

    @Test
    void rejectsBankAccountWhenAccountNumberTooLong() {
        BankAccountRequest request = new BankAccountRequest(
                "John Doe",
                "HDFC Bank",
                "1234567890123",  // 13 digits - too long
                "HDFC0001234",
                "SAVINGS",
                "123456",
                BigDecimal.valueOf(10000)
        );

        Set<ConstraintViolation<BankAccountRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v ->
                "accountNumber".equals(v.getPropertyPath().toString())
                        && "Account number must be 8-12 digits".equals(v.getMessage())));
    }

    @Test
    void acceptsBankAccountWhenAccountNumberIn8DigitRange() {
        BankAccountRequest request = new BankAccountRequest(
                "John Doe",
                "HDFC Bank",
                "12345678",  // 8 digits - valid
                "HDFC0001234",
                "SAVINGS",
                "123456",
                BigDecimal.valueOf(10000)
        );

        Set<ConstraintViolation<BankAccountRequest>> violations = validator.validate(request);

        assertFalse(violations.stream().anyMatch(v -> "accountNumber".equals(v.getPropertyPath().toString())));
    }

    @Test
    void acceptsBankAccountWhenAccountNumberIn12DigitRange() {
        BankAccountRequest request = new BankAccountRequest(
                "John Doe",
                "HDFC Bank",
                "123456789012",  // 12 digits - valid
                "HDFC0001234",
                "SAVINGS",
                "123456",
                BigDecimal.valueOf(10000)
        );

        Set<ConstraintViolation<BankAccountRequest>> violations = validator.validate(request);

        assertFalse(violations.stream().anyMatch(v -> "accountNumber".equals(v.getPropertyPath().toString())));
    }

    @Test
    void rejectsBeneficiaryWhenAccountNumberTooShort() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                "Rahul Kumar",
                "1234567",  // 7 digits - too short
                "HDFC0001234"
        );

        Set<ConstraintViolation<BeneficiaryRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v ->
                "accountNumber".equals(v.getPropertyPath().toString())
                        && "Account number must be 8-12 digits".equals(v.getMessage())));
    }

    @Test
    void rejectsBeneficiaryWhenAccountNumberTooLong() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                "Rahul Kumar",
                "1234567890123",  // 13 digits - too long
                "HDFC0001234"
        );

        Set<ConstraintViolation<BeneficiaryRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v ->
                "accountNumber".equals(v.getPropertyPath().toString())
                        && "Account number must be 8-12 digits".equals(v.getMessage())));
    }

    @Test
    void acceptsBeneficiaryWhenAccountNumberIn8DigitRange() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                "Rahul Kumar",
                "12345678",  // 8 digits - valid
                "HDFC0001234"
        );

        Set<ConstraintViolation<BeneficiaryRequest>> violations = validator.validate(request);

        assertFalse(violations.stream().anyMatch(v -> "accountNumber".equals(v.getPropertyPath().toString())));
    }

    @Test
    void acceptsBeneficiaryWhenAccountNumberIn12DigitRange() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                "Rahul Kumar",
                "123456789012",  // 12 digits - valid
                "HDFC0001234"
        );

        Set<ConstraintViolation<BeneficiaryRequest>> violations = validator.validate(request);

        assertFalse(violations.stream().anyMatch(v -> "accountNumber".equals(v.getPropertyPath().toString())));
    }
}

