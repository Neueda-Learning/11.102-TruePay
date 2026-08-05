package org.example.truepay.api;

import org.example.truepay.model.BankAccount;
import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.BankAccountRepository;
import org.example.truepay.repository.AuditLogRepository;
import org.example.truepay.repository.FraudAlertRepository;
import org.example.truepay.repository.PaymentLimitRepository;
import org.example.truepay.repository.PaymentRepository;
import org.example.truepay.repository.PaymentStatusHistoryRepository;
import org.example.truepay.repository.UserProfileRepository;
import org.example.truepay.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UpiPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentStatusHistoryRepository paymentStatusHistoryRepository;

    @Autowired
    private FraudAlertRepository fraudAlertRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PaymentLimitRepository paymentLimitRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserProfile user;
    private BankAccount source;

    @BeforeEach
    void cleanAndSeed() {
        fraudAlertRepository.deleteAll();
        auditLogRepository.deleteAll();
        paymentStatusHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        paymentLimitRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();

        user = new UserProfile();
        user.setFullName("UPI Test User");
        user.setEmail("upi.test@truepay.local");
        user.setMobile("9999999998");
        user.setAppPinHash(passwordEncoder.encode("1234"));
        user.setPasswordHash(passwordEncoder.encode("secret-pass"));
        user = userProfileRepository.save(user);

        source = new BankAccount();
        source.setUser(user);
        source.setAccountHolderName(user.getFullName());
        source.setBankName("HDFC Bank");
        source.setAccountNumber("123456789012");
        source.setIfscCode("HDFC0001234");
        source.setBankPinHash(passwordEncoder.encode("123456"));
        source.setAccountType("SAVINGS");
        source.setBalance(new BigDecimal("10000.00"));
        source = bankAccountRepository.save(source);

        UserProfile receiver = new UserProfile();
        receiver.setFullName("Coffee Merchant");
        receiver.setEmail("coffee@truepay.local");
        receiver.setMobile("9876543210");
        receiver.setAppPinHash(passwordEncoder.encode("4321"));
        receiver.setPasswordHash(passwordEncoder.encode("merchant-pass"));
        receiver = userProfileRepository.save(receiver);

        BankAccount receiverAccount = new BankAccount();
        receiverAccount.setUser(receiver);
        receiverAccount.setAccountHolderName(receiver.getFullName());
        receiverAccount.setBankName("SBI");
        receiverAccount.setAccountNumber("999988887777");
        receiverAccount.setIfscCode("SBIN0004321");
        receiverAccount.setBankPinHash(passwordEncoder.encode("654321"));
        receiverAccount.setAccountType("SAVINGS");
        receiverAccount.setBalance(new BigDecimal("1000.00"));
        bankAccountRepository.save(receiverAccount);
    }

    @Test
    void payToUpiSucceedsWithoutAppPinField() throws Exception {
        String payload = """
                {
                  "sourceAccountId": %d,
                  "amount": 250.00,
                  "currency": "INR",
                  "destinationUpiId": "coffee@upi",
                  "bankPin": "123456"
                }
                """.formatted(source.getId());

        mockMvc.perform(post("/api/v1/payments/pay-to-upi")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("UPI"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.destinationUpiId").value("coffee@upi"))
                .andExpect(jsonPath("$.errorCode").isEmpty());

        List<Payment> saved = paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertEquals(1, saved.size(), "One UPI payment should be persisted");
        assertEquals(PaymentMethod.UPI, saved.get(0).getMethod());
        assertEquals("coffee@upi", saved.get(0).getDestinationUpiId());
    }

    @Test
    void payToUpiDashboardEndpointSucceedsWithSourceAccountNumber() throws Exception {
        String payload = """
                {
                  "sourceAccount": "%s",
                  "receiverType": "UPI",
                  "receiver": "coffee@upi",
                  "amount": 150.00,
                  "currency": "INR",
                  "bankPin": "123456"
                }
                """.formatted(source.getAccountNumber());

        mockMvc.perform(post("/api/v1/payments/upi")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("UPI"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.destinationUpiId").value("coffee@upi"))
                .andExpect(jsonPath("$.errorCode").isEmpty());

        List<Payment> saved = paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertEquals(1, saved.size(), "One dashboard UPI payment should be persisted");
        assertEquals(source.getId(), saved.get(0).getSourceAccount().getId());
        assertNotNull(saved.get(0).getIdempotencyKey(), "UPI payments should carry an idempotency key");
    }

    @Test
    void payToUpiRejectsInvalidBankPinFormat() throws Exception {
        String payload = """
                {
                  "sourceAccountId": %d,
                  "amount": 100.00,
                  "currency": "INR",
                  "destinationUpiId": "merchant@upi",
                  "bankPin": "1234"
                }
                """.formatted(source.getId());

        mockMvc.perform(post("/api/v1/payments/pay-to-upi")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bankPin")));

        assertFalse(paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                        .anyMatch(p -> "merchant@upi".equals(p.getDestinationUpiId())),
                "Invalid requests should not persist a payment");
    }
}

