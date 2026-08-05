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

class BankPaymentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ───── Helper ─────────────────────────────────────────────────────────────

    /** Builds a fully-valid request so individual tests can vary one field at a time. */
    private BankPaymentRequest valid() {
        return new BankPaymentRequest(
                1L,                     // sourceAccountId
                new BigDecimal("500.00"), // amount
                "INR",                  // currency
                null,                   // beneficiaryId (optional)
                "Rahul Kumar",          // receiverName
                "123456789012",         // destinationAccount
                "HDFC0001234",          // destinationIfsc
                "August rent",          // reference (optional)
                "bank-txn-001",         // idempotencyKey
                "123456"                // bankPin
        );
    }

    private boolean hasViolationOn(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream().anyMatch(v -> field.equals(v.getPropertyPath().toString()));
    }

    // ───── sourceAccountId ────────────────────────────────────────────────────

    @Test
    void rejectsWhenSourceAccountIdIsNull() {
        BankPaymentRequest request = new BankPaymentRequest(
                null, new BigDecimal("500.00"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "sourceAccountId"),
                "sourceAccountId should be required");
    }

    @Test
    void acceptsValidSourceAccountId() {
        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(valid());

        assertFalse(hasViolationOn(violations, "sourceAccountId"));
    }

    // ───── amount ─────────────────────────────────────────────────────────────

    @Test
    void rejectsWhenAmountIsNull() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, null, "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "amount"),
                "amount should be required");
    }

    @Test
    void rejectsWhenAmountIsZero() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, BigDecimal.ZERO, "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "amount"),
                "amount of 0 should be rejected (min is 0.01)");
    }

    @Test
    void rejectsWhenAmountIsBelowMinimum() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("0.00"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "amount"),
                "amount below 0.01 should be rejected");
    }

    @Test
    void acceptsWhenAmountIsExactlyMinimum() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("0.01"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertFalse(hasViolationOn(violations, "amount"),
                "amount of 0.01 should be accepted");
    }

    @Test
    void acceptsLargeAmount() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("999999.99"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertFalse(hasViolationOn(violations, "amount"));
    }

    // ───── currency ───────────────────────────────────────────────────────────

    @Test
    void rejectsWhenCurrencyIsBlank() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "currency"),
                "blank currency should be rejected");
    }

    @Test
    void rejectsWhenCurrencyIsLowercase() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "inr",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "currency"),
                "lowercase currency should be rejected");
    }

    @Test
    void rejectsWhenCurrencyIsTooShort() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "IN",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "currency"),
                "2-char currency should be rejected");
    }

    @Test
    void rejectsWhenCurrencyIsTooLong() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "INRR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "currency"),
                "4-char currency should be rejected");
    }

    @Test
    void acceptsValidCurrencyINR() {
        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(valid());

        assertFalse(hasViolationOn(violations, "currency"));
    }

    @Test
    void acceptsValidCurrencyUSD() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "USD",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertFalse(hasViolationOn(violations, "currency"));
    }

    // ───── idempotencyKey ─────────────────────────────────────────────────────

    @Test
    void rejectsWhenIdempotencyKeyIsBlank() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "idempotencyKey"),
                "blank idempotencyKey should be rejected");
    }

    @Test
    void rejectsWhenIdempotencyKeyIsNull() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, null, "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "idempotencyKey"),
                "null idempotencyKey should be rejected");
    }

    @Test
    void acceptsValidIdempotencyKey() {
        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(valid());

        assertFalse(hasViolationOn(violations, "idempotencyKey"));
    }

    // ───── bankPin ────────────────────────────────────────────────────────────

    @Test
    void rejectsWhenBankPinHasFewDigits() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "1234"   // 4 digits - too short
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "bankPin"),
                "4-digit bank PIN should be rejected");
    }

    @Test
    void rejectsWhenBankPinHasTooManyDigits() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "1234567"   // 7 digits - too long
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "bankPin"),
                "7-digit bank PIN should be rejected");
    }

    @Test
    void rejectsWhenBankPinContainsLetters() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", "12345a"   // contains letter
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "bankPin"),
                "bank PIN with letters should be rejected");
    }

    @Test
    void acceptsValidSixDigitBankPin() {
        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(valid());

        assertFalse(hasViolationOn(violations, "bankPin"),
                "6-digit numeric bank PIN should be accepted");
    }

    @Test
    void acceptsNullBankPin() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "INR",
                null, "Rahul Kumar", "123456789012", "HDFC0001234",
                null, "bank-txn-001", null   // null is allowed - @Pattern only runs on non-null
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        assertFalse(hasViolationOn(violations, "bankPin"),
                "null bank PIN should pass bean validation (@Pattern skips null)");
    }

    // ───── optional fields (no validation annotation) ─────────────────────────

    @Test
    void acceptsRequestWithAllOptionalFieldsNull() {
        BankPaymentRequest request = new BankPaymentRequest(
                1L, new BigDecimal("500.00"), "INR",
                null,   // beneficiaryId - optional
                null,   // receiverName  - optional
                null,   // destinationAccount - optional
                null,   // destinationIfsc    - optional
                null,   // reference          - optional
                "bank-txn-001", "123456"
        );

        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(request);

        // Bean validation passes — business-level rules (e.g. account required for BANK) are handled in PaymentService
        assertTrue(violations.isEmpty() || violations.stream().noneMatch(v ->
                "beneficiaryId".equals(v.getPropertyPath().toString())
                || "receiverName".equals(v.getPropertyPath().toString())
                || "destinationAccount".equals(v.getPropertyPath().toString())
                || "destinationIfsc".equals(v.getPropertyPath().toString())
                || "reference".equals(v.getPropertyPath().toString())),
                "Optional fields should not produce bean validation violations");
    }

    // ───── full valid request ─────────────────────────────────────────────────

    @Test
    void acceptsCompletelyValidRequest() {
        Set<ConstraintViolation<BankPaymentRequest>> violations = validator.validate(valid());

        assertTrue(violations.isEmpty(),
                "A fully-valid BankPaymentRequest should have no violations");
    }
}

