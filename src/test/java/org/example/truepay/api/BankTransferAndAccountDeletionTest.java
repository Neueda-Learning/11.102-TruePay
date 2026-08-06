package org.example.truepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.truepay.model.BankAccount;
import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.PaymentStatus;
import org.example.truepay.model.ReceiverType;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BankTransferAndAccountDeletionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    private UserProfile owner;
    private BankAccount source;
    private BankAccount destination;

    @BeforeEach
    void cleanAndSeed() {
        fraudAlertRepository.deleteAll();
        auditLogRepository.deleteAll();
        paymentStatusHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        paymentLimitRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();

        owner = createUser("owner-bank@truepay.local", "9000001111");
        source = createAccount(owner, "111122223333", "HDFC0001234", "123456", "1000.00");

        UserProfile receiver = createUser("receiver-bank@truepay.local", "9000001112");
        destination = createAccount(receiver, "444455556666", "HDFC0005678", "654321", "200.00");
    }

    @Test
    void bankTransferEndpointSucceedsAndUpdatesHistory() throws Exception {
        String payload = """
                {
                  "sourceAccount": "%s",
                  "destinationAccount": "%s",
                  "destinationIfsc": "%s",
                  "amount": 250.00,
                  "currency": "INR",
                  "bankPin": "123456"
                }
                """.formatted(source.getAccountNumber(), destination.getAccountNumber(), destination.getIfscCode());

        mockMvc.perform(post("/api/v1/payments/bank-transfer")
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/payments")
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void deleteAccountDetachesHistoricalPaymentsAndDeletesAccount() throws Exception {
        Payment archived = new Payment();
        archived.setUser(owner);
        archived.setSourceAccount(source);
        archived.setMethod(PaymentMethod.UPI);
        archived.setReceiverType(ReceiverType.UPI_ID);
        archived.setAmount(new BigDecimal("10.00"));
        archived.setCurrency("INR");
        archived.setDestinationUpiId("merchant@upi");
        archived.setStatus(PaymentStatus.SUCCESS);
        archived = paymentRepository.save(archived);

        source.setBalance(BigDecimal.ZERO);
        bankAccountRepository.save(source);

        mockMvc.perform(delete("/api/v1/bank-accounts/{id}", source.getId())
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId()))
                .andExpect(status().isOk());

        assertFalse(bankAccountRepository.findById(source.getId()).isPresent(), "Source account should be deleted");
        Payment savedPayment = paymentRepository.findById(archived.getId()).orElseThrow();
        assertNull(savedPayment.getSourceAccount(), "Payment should retain history even after source account deletion");
    }

    @Test
    void paymentListHandlesLegacyStatusRows() throws Exception {
        Payment legacy = new Payment();
        legacy.setUser(owner);
        legacy.setSourceAccount(source);
        legacy.setMethod(PaymentMethod.UPI);
        legacy.setReceiverType(ReceiverType.UPI_ID);
        legacy.setAmount(new BigDecimal("25.00"));
        legacy.setCurrency("INR");
        legacy.setDestinationUpiId("legacy@upi");
        legacy.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(legacy);

        mockMvc.perform(get("/api/v1/payments")
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void cancelledTransferAppearsInPaymentAndAuditHistory() throws Exception {
        String payload = """
                {
                  "sourceAccount": "%s",
                  "destinationAccount": "%s",
                  "destinationIfsc": "%s",
                  "amount": 150.00,
                  "currency": "INR",
                  "bankPin": "123456"
                }
                """.formatted(source.getAccountNumber(), destination.getAccountNumber(), destination.getIfscCode());

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments/bank-transfer")
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode created = OBJECT_MAPPER.readTree(createResult.getResponse().getContentAsString());
        String paymentId = created.get("id").asText();

        mockMvc.perform(post("/api/v1/payments/{paymentId}/cancel", paymentId)
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/payments")
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(paymentId))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/payments/audits")
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    private UserProfile createUser(String email, String mobile) {
        UserProfile profile = new UserProfile();
        profile.setFullName("Test User");
        profile.setEmail(email);
        profile.setMobile(mobile);
        profile.setAppPinHash(passwordEncoder.encode("1234"));
        profile.setPasswordHash(passwordEncoder.encode("Password@123"));
        return userProfileRepository.save(profile);
    }

    private BankAccount createAccount(UserProfile user,
                                      String accountNumber,
                                      String ifsc,
                                      String pin,
                                      String balance) {
        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setAccountHolderName(user.getFullName());
        account.setBankName("HDFC Bank");
        account.setAccountNumber(accountNumber);
        account.setIfscCode(ifsc);
        account.setBankPinHash(passwordEncoder.encode(pin));
        account.setAccountType("SAVINGS");
        account.setBalance(new BigDecimal(balance));
        return bankAccountRepository.save(account);
    }
}

