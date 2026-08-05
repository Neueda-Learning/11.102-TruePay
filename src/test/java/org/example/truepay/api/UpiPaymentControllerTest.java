package org.example.truepay.api;

import org.example.truepay.model.BankAccount;
import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.BankAccountRepository;
import org.example.truepay.repository.FraudAlertRepository;
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
    private PasswordEncoder passwordEncoder;

    private UserProfile user;
    private BankAccount source;

    @BeforeEach
    void cleanAndSeed() {
        fraudAlertRepository.deleteAll();
        paymentStatusHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
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
    }

    @Test
    void payToUpiSucceedsWithoutAppPinField() throws Exception {
        String payload = """
                {
                  "sourceAccountId": %d,
                  "amount": 250.00,
                  "currency": "INR",
                  "destinationUpiId": "coffee@upi",
                  "idempotencyKey": "upi-no-app-pin-001",
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
    void payToUpiRejectsInvalidBankPinFormat() throws Exception {
        String payload = """
                {
                  "sourceAccountId": %d,
                  "amount": 100.00,
                  "currency": "INR",
                  "destinationUpiId": "merchant@upi",
                  "idempotencyKey": "upi-invalid-pin-001",
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
                        .anyMatch(p -> "upi-invalid-pin-001".equals(p.getIdempotencyKey())),
                "Invalid requests should not persist a payment");
    }
}

