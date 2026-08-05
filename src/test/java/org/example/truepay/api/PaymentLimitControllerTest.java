package org.example.truepay.api;

import org.example.truepay.model.BankAccount;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.AuditLogRepository;
import org.example.truepay.repository.BankAccountRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentLimitControllerTest {

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

        user = createUser("limits-owner@truepay.local", "9999991100");
        source = createAccount(user, "444455556661", "HDFC0003333", "123456", "1000.00");

        UserProfile receiver = createUser("merchant@truepay.local", "9999991101");
        createAccount(receiver, "444455556662", "HDFC0004444", "654321", "500.00");
    }

    @Test
    void blocksPerTransactionWhenEnabled() throws Exception {
        String limitPayload = """
                {
                  "dailyEnabled": false,
                  "dailyLimit": null,
                  "monthlyEnabled": false,
                  "monthlyLimit": null,
                  "perTransactionEnabled": true,
                  "perTransactionLimit": 100.00
                }
                """;

        mockMvc.perform(put("/api/v1/payment-limits")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perTransactionEnabled").value(true))
                .andExpect(jsonPath("$.perTransactionLimit").value(100.00));

        String paymentPayload = """
                {
                  "sourceAccount": "%s",
                  "receiverType": "UPI",
                  "receiver": "merchant@upi",
                  "amount": 101.00,
                  "currency": "INR",
                  "bankPin": "123456"
                }
                """.formatted(source.getAccountNumber());

        mockMvc.perform(post("/api/v1/payments/upi")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Per-transaction transfer limit exceeded"));
    }

    @Test
    void blocksDailyLimitAfterSuccessfulPayments() throws Exception {
        String limitPayload = """
                {
                  "dailyEnabled": true,
                  "dailyLimit": 100.00,
                  "monthlyEnabled": false,
                  "monthlyLimit": null,
                  "perTransactionEnabled": false,
                  "perTransactionLimit": null
                }
                """;

        mockMvc.perform(put("/api/v1/payment-limits")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(limitPayload))
                .andExpect(status().isOk());

        String first = """
                {
                  "sourceAccount": "%s",
                  "receiverType": "UPI",
                  "receiver": "merchant@upi",
                  "amount": 60.00,
                  "currency": "INR",
                  "bankPin": "123456"
                }
                """.formatted(source.getAccountNumber());

        mockMvc.perform(post("/api/v1/payments/upi")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        String second = """
                {
                  "sourceAccount": "%s",
                  "receiverType": "UPI",
                  "receiver": "merchant@upi",
                  "amount": 50.00,
                  "currency": "INR",
                  "bankPin": "123456"
                }
                """.formatted(source.getAccountNumber());

        mockMvc.perform(post("/api/v1/payments/upi")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(second))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Daily transfer limit exceeded"));
    }

    private UserProfile createUser(String email, String mobile) {
        UserProfile profile = new UserProfile();
        profile.setFullName("Limits User");
        profile.setEmail(email);
        profile.setMobile(mobile);
        profile.setAppPinHash(passwordEncoder.encode("1234"));
        profile.setPasswordHash(passwordEncoder.encode("Password@123"));
        return userProfileRepository.save(profile);
    }

    private BankAccount createAccount(UserProfile owner,
                                      String accountNumber,
                                      String ifsc,
                                      String pin,
                                      String balance) {
        BankAccount account = new BankAccount();
        account.setUser(owner);
        account.setAccountHolderName(owner.getFullName());
        account.setBankName("HDFC Bank");
        account.setAccountNumber(accountNumber);
        account.setIfscCode(ifsc);
        account.setBankPinHash(passwordEncoder.encode(pin));
        account.setAccountType("SAVINGS");
        account.setBalance(new BigDecimal(balance));
        return bankAccountRepository.save(account);
    }
}

