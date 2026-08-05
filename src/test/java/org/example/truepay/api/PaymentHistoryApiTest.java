package org.example.truepay.api;

import org.example.truepay.model.BankAccount;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.AuditLogRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentHistoryApiTest {

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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private FraudAlertRepository fraudAlertRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserProfile user;
    private BankAccount source;
    private BankAccount destination;

    @BeforeEach
    void cleanAndSeed() {
        fraudAlertRepository.deleteAll();
        auditLogRepository.deleteAll();
        paymentStatusHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();

        user = createUser("history-owner@truepay.local", "9000000001");
        source = createAccount(user, "123456789012", "HDFC0001234", "123456", "1000.00");
        destination = createAccount(user, "123456789013", "HDFC0001235", "654321", "100.00");
    }

    @Test
    void exposesPaymentAndAuditHistoryEndpoints() throws Exception {
        String payload = """
                {
                  "sourceAccountId": %d,
                  "amount": 250.00,
                  "currency": "INR",
                  "receiverName": "Self Target",
                  "destinationAccount": "%s",
                  "destinationIfsc": "%s",
                  "reference": "history test",
                  "bankPin": "123456"
                }
                """.formatted(source.getId(), destination.getAccountNumber(), destination.getIfscCode());

        mockMvc.perform(post("/api/v1/payments/pay-to-bank")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/payments/history/{userId}", user.getId())
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId", containsString("TXN")))
                .andExpect(jsonPath("$[0].paymentMethod").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/audit/history/{userId}", user.getId())
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId", containsString("TXN")))
                .andExpect(jsonPath("$[0].action").value("PAYMENT_SUCCESS"));
    }

    private UserProfile createUser(String email, String mobile) {
        UserProfile profile = new UserProfile();
        profile.setFullName("History User");
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
        account.setBankName("HDFC");
        account.setAccountNumber(accountNumber);
        account.setIfscCode(ifsc);
        account.setBankPinHash(passwordEncoder.encode(pin));
        account.setAccountType("SAVINGS");
        account.setBalance(new BigDecimal(balance));
        return bankAccountRepository.save(account);
    }
}

