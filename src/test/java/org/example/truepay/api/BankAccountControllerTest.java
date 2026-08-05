package org.example.truepay.api;

import org.example.truepay.model.BankAccount;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.AuditLogRepository;
import org.example.truepay.repository.BankAccountRepository;
import org.example.truepay.repository.FraudAlertRepository;
import org.example.truepay.repository.PaymentRepository;
import org.example.truepay.repository.PaymentLimitRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BankAccountControllerTest {

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
        user.setFullName("Asha Sharma");
        user.setEmail("asha.sharma@truepay.local");
        user.setMobile("9876501234");
        user.setAppPinHash(passwordEncoder.encode("1234"));
        user.setPasswordHash(passwordEncoder.encode("Password@123"));
        user = userProfileRepository.save(user);
    }

    @Test
    void addAccountUsesLoggedInUsersFullNameWhenHolderFieldIsNotProvided() throws Exception {
        String payload = """
                {
                  "bankName": "ICICI Bank",
                  "accountNumber": "12345678",
                  "ifscCode": "icic0001234",
                  "accountType": "savings",
                  "bankPin": "123456",
                  "openingBalance": 1500.00
                }
                """;

        mockMvc.perform(post("/api/v1/bank-accounts")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountHolderName").value("Asha Sharma"))
                .andExpect(jsonPath("$.bankName").value("ICICI Bank"))
                .andExpect(jsonPath("$.ifscCode").value("ICIC0001234"))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value(1500.00));

        Optional<BankAccount> saved = bankAccountRepository.findByAccountNumber("12345678");
        assertTrue(saved.isPresent(), "Bank account should be created");
        assertEquals("Asha Sharma", saved.get().getAccountHolderName());
        assertEquals("ICIC0001234", saved.get().getIfscCode());
        assertEquals("SAVINGS", saved.get().getAccountType());
    }

    @Test
    void addAccountRejectsDuplicateAccountNumberWithValidationMessage() throws Exception {
        BankAccount existing = new BankAccount();
        existing.setUser(user);
        existing.setAccountHolderName(user.getFullName());
        existing.setBankName("Existing Bank");
        existing.setAccountNumber("12345678");
        existing.setIfscCode("HDFC0001111");
        existing.setBankPinHash(passwordEncoder.encode("123456"));
        existing.setAccountType("SAVINGS");
        existing.setBalance(java.math.BigDecimal.ZERO);
        bankAccountRepository.save(existing);

        String payload = """
                {
                  "bankName": "ICICI Bank",
                  "accountNumber": "12345678",
                  "ifscCode": "icic0001234",
                  "accountType": "savings",
                  "bankPin": "123456",
                  "openingBalance": 1500.00
                }
                """;

        mockMvc.perform(post("/api/v1/bank-accounts")
                        .sessionAttr(SessionService.SESSION_USER_ID, user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Bank account with this account number already exists"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}

