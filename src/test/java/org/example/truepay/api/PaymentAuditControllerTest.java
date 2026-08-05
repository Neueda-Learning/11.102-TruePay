package org.example.truepay.api;

import org.example.truepay.model.BankAccount;
import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.PaymentStatus;
import org.example.truepay.model.PaymentStatusHistory;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.BankAccountRepository;
import org.example.truepay.repository.PaymentRepository;
import org.example.truepay.repository.PaymentStatusHistoryRepository;
import org.example.truepay.repository.UserProfileRepository;
import org.example.truepay.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentAuditControllerTest {

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

    @BeforeEach
    void cleanDatabase() {
        paymentStatusHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();
    }

    @Test
    void returnsAuditHistoryForAllTransactionsOfCurrentUser() throws Exception {
        UserProfile owner = createUser("owner@truepay.local", "9999999990");
        BankAccount source = createAccount(owner, "123456789012", "HDFC0001234");

        Payment upiPayment = createPayment(owner, source, PaymentMethod.UPI, new BigDecimal("250.00"), "INR", "upi-order-001");
        upiPayment.setDestinationUpiId("coffee@upi");
        paymentRepository.save(upiPayment);

        Payment bankPayment = createPayment(owner, source, PaymentMethod.BANK, new BigDecimal("4000.00"), "INR", "bank-order-001");
        bankPayment.setReceiverName("Rahul Kumar");
        bankPayment.setDestinationAccount("123456789098");
        bankPayment.setDestinationIfsc("HDFC0001111");
        bankPayment.setReferenceRemark("August rent");
        paymentRepository.save(bankPayment);

        createHistory(upiPayment, PaymentStatus.CREATED, "API", "Payment submitted", Instant.parse("2026-08-05T10:00:00Z"));
        createHistory(upiPayment, PaymentStatus.COMPLETED, "SYSTEM", "Payment completed successfully", Instant.parse("2026-08-05T10:01:00Z"));
        createHistory(bankPayment, PaymentStatus.CREATED, "API", "Bank transfer submitted", Instant.parse("2026-08-05T10:02:00Z"));

        UserProfile otherUser = createUser("other@truepay.local", "9999999991");
        BankAccount otherAccount = createAccount(otherUser, "123456789013", "HDFC0005678");
        Payment otherPayment = createPayment(otherUser, otherAccount, PaymentMethod.UPI, new BigDecimal("99.00"), "INR", "upi-order-002");
        paymentRepository.save(otherPayment);
        createHistory(otherPayment, PaymentStatus.CREATED, "API", "Should not be visible", Instant.parse("2026-08-05T10:03:00Z"));

        mockMvc.perform(get("/api/v1/payments/audits")
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].paymentId").value(bankPayment.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[0].triggeredBy").value("API"))
                .andExpect(jsonPath("$[0].receiver").value("Rahul Kumar"))
                .andExpect(jsonPath("$[0].idempotencyKey").value("bank-order-001"))
                .andExpect(jsonPath("$[0].referenceRemark").value("August rent"))
                .andExpect(jsonPath("$[1].paymentId").value(upiPayment.getId().toString()))
                .andExpect(jsonPath("$[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$[1].idempotencyKey").value("upi-order-001"))
                .andExpect(jsonPath("$[2].status").value("CREATED"));
    }

    @Test
    void returnsEmptyAuditHistoryWhenUserHasNoTransactions() throws Exception {
        UserProfile owner = createUser("empty@truepay.local", "9999999992");

        mockMvc.perform(get("/api/v1/payments/audits")
                        .sessionAttr(SessionService.SESSION_USER_ID, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private UserProfile createUser(String email, String mobile) {
        UserProfile user = new UserProfile();
        user.setFullName("Test User");
        user.setEmail(email);
        user.setMobile(mobile);
        user.setAppPinHash("hashed-app-pin");
        user.setPasswordHash("hashed-password");
        return userProfileRepository.save(user);
    }

    private BankAccount createAccount(UserProfile user, String accountNumber, String ifsc) {
        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setAccountHolderName(user.getFullName());
        account.setBankName("HDFC Bank");
        account.setAccountNumber(accountNumber);
        account.setIfscCode(ifsc);
        account.setBankPinHash("hashed-bank-pin");
        account.setAccountType("SAVINGS");
        account.setBalance(new BigDecimal("100000.00"));
        return bankAccountRepository.save(account);
    }

    private Payment createPayment(UserProfile user,
                                  BankAccount source,
                                  PaymentMethod method,
                                  BigDecimal amount,
                                  String currency,
                                  String idempotencyKey) {
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setSourceAccount(source);
        payment.setMethod(method);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus(PaymentStatus.CREATED);
        return payment;
    }

    private void createHistory(Payment payment,
                               PaymentStatus status,
                               String triggeredBy,
                               String notes,
                               Instant changedAt) {
        PaymentStatusHistory history = new PaymentStatusHistory();
        history.setPayment(payment);
        history.setStatus(status);
        history.setTriggeredBy(triggeredBy);
        history.setNotes(notes);
        history.setChangedAt(changedAt);
        paymentStatusHistoryRepository.save(history);
    }
}

