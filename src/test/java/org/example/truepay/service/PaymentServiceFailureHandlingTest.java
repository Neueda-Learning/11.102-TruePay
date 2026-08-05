package org.example.truepay.service;

import org.example.truepay.api.BankPaymentRequest;
import org.example.truepay.api.UpiPaymentRequest;
import org.example.truepay.model.BankAccount;
import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.PaymentStatus;
import org.example.truepay.model.ReceiverType;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.BankAccountRepository;
import org.example.truepay.repository.AuditLogRepository;
import org.example.truepay.repository.PaymentLimitRepository;
import org.example.truepay.repository.PaymentRepository;
import org.example.truepay.repository.PaymentStatusHistoryRepository;
import org.example.truepay.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PaymentServiceFailureHandlingTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentStatusHistoryRepository paymentStatusHistoryRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PaymentLimitRepository paymentLimitRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        auditLogRepository.deleteAll();
        paymentStatusHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        paymentLimitRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();
    }

    @Test
    void successfulPaymentMarksStatusSuccess() {
        UserProfile user = createUser("success@truepay.local", "9999999001");
        BankAccount source = createAccount(user, "111122223333", "HDFC0001111", new BigDecimal("1000.00"), "123456");
        BankAccount destination = createAccount(user, "444455556666", "HDFC0002222", new BigDecimal("10.00"), "654321");

        BankPaymentRequest request = new BankPaymentRequest(
                source.getId(),
                new BigDecimal("250.00"),
                "INR",
                null,
                "Receiver",
                destination.getAccountNumber(),
                destination.getIfscCode(),
                "rent",
                "123456"
        );

        Payment payment = paymentService.createBankPayment(user.getId(), request);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(new BigDecimal("750.00"), reloadAccount(source.getId()).getBalance());
        assertEquals(new BigDecimal("260.00"), reloadAccount(destination.getId()).getBalance());
    }

    @Test
    void insufficientBalanceMarksPaymentFailed() {
        UserProfile user = createUser("insufficient@truepay.local", "9999999002");
        BankAccount source = createAccount(user, "111122223334", "HDFC0001112", new BigDecimal("50.00"), "123456");

        UpiPaymentRequest request = new UpiPaymentRequest(
                source.getId(),
                new BigDecimal("200.00"),
                "INR",
                "merchant@upi",
                "123456"
        );

        Payment payment = paymentService.createUpiPayment(user.getId(), request);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Insufficient balance", payment.getFailureReason());
        assertEquals(new BigDecimal("50.00"), reloadAccount(source.getId()).getBalance());
        assertNotNull(paymentRepository.findById(payment.getId()).orElse(null));
    }

    @Test
    void invalidAccountMarksPaymentFailed() {
        UserProfile user = createUser("invalid-account@truepay.local", "9999999003");
        BankAccount source = createAccount(user, "111122223335", "HDFC0001113", new BigDecimal("500.00"), "123456");

        BankPaymentRequest request = new BankPaymentRequest(
                source.getId(),
                new BigDecimal("100.00"),
                "INR",
                null,
                "Unknown Receiver",
                "999988887777",
                "HDFC0009999",
                "test",
                "123456"
        );

        Payment payment = paymentService.createBankPayment(user.getId(), request);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Invalid destination account", payment.getFailureReason());
        assertEquals(new BigDecimal("500.00"), reloadAccount(source.getId()).getBalance());
    }

    @Test
    void wrongBankPinMarksPaymentFailed() {
        UserProfile user = createUser("wrong-pin@truepay.local", "9999999007");
        BankAccount source = createAccount(user, "111122223338", "HDFC0001116", new BigDecimal("500.00"), "123456");

        UpiPaymentRequest request = new UpiPaymentRequest(
                source.getId(),
                new BigDecimal("100.00"),
                "INR",
                "merchant@upi",
                "000000"
        );

        Payment payment = paymentService.createUpiPayment(user.getId(), request);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Invalid bank PIN", payment.getFailureReason());
    }

    @Test
    void missingUpiReceiverMarksPaymentFailed() {
        UserProfile user = createUser("missing-upi@truepay.local", "9999999008");
        BankAccount source = createAccount(user, "111122223339", "HDFC0001117", new BigDecimal("500.00"), "123456");

        UpiPaymentRequest request = new UpiPaymentRequest(
                source.getId(),
                new BigDecimal("100.00"),
                "INR",
                "notfound@upi",
                "123456"
        );

        Payment payment = paymentService.createUpiPayment(user.getId(), request);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Receiver not found", payment.getFailureReason());
    }

    @Test
    void missingSourceAccountMarksPaymentFailed() {
        UserProfile user = createUser("missing-source@truepay.local", "9999999006");

        UpiPaymentRequest request = new UpiPaymentRequest(
                999999L,
                new BigDecimal("100.00"),
                "INR",
                "merchant@upi",
                "123456"
        );

        TruePayException ex = assertThrows(TruePayException.class,
                () -> paymentService.createUpiPayment(user.getId(), request));
        assertEquals("Source account not found", ex.getMessage());
    }

    @Test
    void invalidAmountMarksPaymentFailed() {
        UserProfile user = createUser("invalid-amount@truepay.local", "9999999004");
        BankAccount source = createAccount(user, "111122223336", "HDFC0001114", new BigDecimal("500.00"), "123456");

        UpiPaymentRequest request = new UpiPaymentRequest(
                source.getId(),
                BigDecimal.ZERO,
                "INR",
                "merchant@upi",
                "123456"
        );

        Payment payment = paymentService.createUpiPayment(user.getId(), request);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Invalid payment amount", payment.getFailureReason());
        assertEquals(new BigDecimal("500.00"), reloadAccount(source.getId()).getBalance());
    }

    @Test
    void pendingPaymentCanBeCancelled() {
        UserProfile user = createUser("cancel@truepay.local", "9999999005");
        BankAccount source = createAccount(user, "111122223337", "HDFC0001115", new BigDecimal("400.00"), "123456");

        Payment pendingPayment = new Payment();
        pendingPayment.setUser(user);
        pendingPayment.setSourceAccount(source);
        pendingPayment.setMethod(PaymentMethod.UPI);
        pendingPayment.setReceiverType(ReceiverType.UPI_ID);
        pendingPayment.setAmount(new BigDecimal("50.00"));
        pendingPayment.setCurrency("INR");
        pendingPayment.setDestinationUpiId("merchant@upi");
        pendingPayment.setStatus(PaymentStatus.PENDING);
        pendingPayment = paymentRepository.save(pendingPayment);

        Payment cancelled = paymentService.cancelPayment(user.getId(), pendingPayment.getId(), "User cancelled from app");

        assertEquals(PaymentStatus.CANCELLED, cancelled.getStatus());
        assertEquals("User cancelled from app", cancelled.getFailureReason());
    }

    private UserProfile createUser(String email, String mobile) {
        UserProfile user = new UserProfile();
        user.setFullName("Test User");
        user.setEmail(email);
        user.setMobile(mobile);
        user.setAppPinHash(passwordEncoder.encode("1234"));
        user.setPasswordHash(passwordEncoder.encode("Password@123"));
        return userProfileRepository.save(user);
    }

    private BankAccount createAccount(UserProfile user,
                                      String accountNumber,
                                      String ifsc,
                                      BigDecimal balance,
                                      String plainBankPin) {
        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setAccountHolderName(user.getFullName());
        account.setBankName("HDFC Bank");
        account.setAccountNumber(accountNumber);
        account.setIfscCode(ifsc);
        account.setBankPinHash(passwordEncoder.encode(plainBankPin));
        account.setAccountType("SAVINGS");
        account.setBalance(balance);
        return bankAccountRepository.save(account);
    }

    private BankAccount reloadAccount(Long accountId) {
        return bankAccountRepository.findById(accountId).orElseThrow();
    }
}


