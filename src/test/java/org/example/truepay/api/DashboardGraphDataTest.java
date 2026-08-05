package org.example.truepay.api;

import org.example.truepay.model.*;
import org.example.truepay.repository.*;
import org.example.truepay.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying the backend data that drives each dashboard graph.
 *
 * Graph → data source mapping:
 *  - volumeChart  (line)     → listPayments() filtered by createdAt  (last 7 days)
 *  - statusChart  (doughnut) → getDashboardSummary().completedPayments / failedPayments / in-progress
 *  - methodChart  (bar)      → listPayments() filtered by PaymentMethod (UPI vs BANK)
 *  - riskChart    (bar)      → getDashboardSummary().completedPayments / failedPayments / fraudAlerts
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DashboardGraphDataTest {

    @Autowired private PaymentService paymentService;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private BankAccountRepository bankAccountRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private FraudAlertRepository fraudAlertRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UserProfile user;
    private BankAccount account;

    // ------------------------------------------------------------------ setup

    @BeforeEach
    void setUp() {
        user = new UserProfile();
        user.setFullName("Test User");
        user.setEmail("testgraph@truepay.com");
        user.setMobile("9876543210");
        user.setAppPinHash(passwordEncoder.encode("1234"));
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user = userProfileRepository.save(user);

        account = new BankAccount();
        account.setUser(user);
        account.setAccountHolderName("Test User");
        account.setBankName("HDFC Bank");
        account.setAccountNumber("11223344");
        account.setIfscCode("HDFC0001234");
        account.setBankPinHash(passwordEncoder.encode("123456"));
        account.setAccountType("SAVINGS");
        account.setBalance(new BigDecimal("100000.00"));
        account = bankAccountRepository.save(account);
    }

    // helper – save a payment directly (bypasses PIN validation)
    private Payment savePayment(PaymentMethod method, PaymentStatus status) {
        Payment p = new Payment();
        p.setUser(user);
        p.setSourceAccount(account);
        p.setMethod(method);
        p.setAmount(new BigDecimal("100.00"));
        p.setCurrency("INR");
        p.setIdempotencyKey("key-" + System.nanoTime());
        p.setStatus(status);
        if (method == PaymentMethod.UPI) {
            p.setDestinationUpiId("merchant@upi");
        }
        return paymentRepository.save(p);
    }

    // ================================================================ TESTS =

    // ----------------------------------------------------------------
    // statusChart — doughnut: Completed / Failed / In-Progress
    // ----------------------------------------------------------------

    @Test
    void statusChart_completedPaymentsCountIsCorrect() {
        savePayment(PaymentMethod.UPI, PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.UPI, PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.UPI, PaymentStatus.FAILED);

        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        assertEquals(2, summary.completedPayments(),
                "statusChart: completedPayments segment must equal number of COMPLETED payments");
    }

    @Test
    void statusChart_failedPaymentsCountIsCorrect() {
        savePayment(PaymentMethod.UPI, PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.UPI, PaymentStatus.FAILED);
        savePayment(PaymentMethod.UPI, PaymentStatus.FAILED);

        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        assertEquals(2, summary.failedPayments(),
                "statusChart: failedPayments segment must equal number of FAILED payments");
    }

    @Test
    void statusChart_inProgressIsCorrectlyCalculated() {
        // In-progress = total - completed - failed
        savePayment(PaymentMethod.UPI, PaymentStatus.COMPLETED);  // completed
        savePayment(PaymentMethod.UPI, PaymentStatus.FAILED);      // failed
        savePayment(PaymentMethod.UPI, PaymentStatus.CREATED);     // in-progress
        savePayment(PaymentMethod.UPI, PaymentStatus.SENT);        // in-progress

        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        long inProgress = summary.totalPayments() - summary.completedPayments() - summary.failedPayments();
        assertEquals(2, inProgress,
                "statusChart: in-progress segment = totalPayments - completedPayments - failedPayments");
    }

    @Test
    void statusChart_returnsZeroSegmentsWhenNoPayments() {
        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        assertEquals(0, summary.totalPayments(),    "statusChart: totalPayments must be 0 with no payments");
        assertEquals(0, summary.completedPayments(),"statusChart: completedPayments must be 0 with no payments");
        assertEquals(0, summary.failedPayments(),   "statusChart: failedPayments must be 0 with no payments");
    }

    // ----------------------------------------------------------------
    // riskChart — bar: Completed / Failed / Fraud Alerts
    // ----------------------------------------------------------------

    @Test
    void riskChart_fraudAlertCountIsCorrect() {
        Payment p1 = savePayment(PaymentMethod.UPI, PaymentStatus.FAILED);
        Payment p2 = savePayment(PaymentMethod.UPI, PaymentStatus.FAILED);

        FraudAlert alert1 = new FraudAlert();
        alert1.setPayment(p1);
        alert1.setRiskScore(90);
        alert1.setReason("High-value transaction detected");
        fraudAlertRepository.save(alert1);

        FraudAlert alert2 = new FraudAlert();
        alert2.setPayment(p2);
        alert2.setRiskScore(75);
        alert2.setReason("Unusual transaction frequency detected");
        fraudAlertRepository.save(alert2);

        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        assertEquals(2, summary.fraudAlerts(),
                "riskChart: fraudAlerts bar must equal number of saved fraud alerts");
    }

    @Test
    void riskChart_returnsZeroFraudAlertsWhenNone() {
        savePayment(PaymentMethod.UPI, PaymentStatus.COMPLETED);

        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        assertEquals(0, summary.fraudAlerts(),
                "riskChart: fraudAlerts bar must be 0 when no fraud alerts exist");
    }

    @Test
    void riskChart_completedAndFailedBarsMatchSummary() {
        savePayment(PaymentMethod.BANK, PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.BANK, PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.BANK, PaymentStatus.FAILED);

        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        assertEquals(2, summary.completedPayments(),
                "riskChart: completed bar must equal completedPayments in summary");
        assertEquals(1, summary.failedPayments(),
                "riskChart: failed bar must equal failedPayments in summary");
    }

    // ----------------------------------------------------------------
    // methodChart — bar: UPI count / Bank Transfer count
    // ----------------------------------------------------------------

    @Test
    void methodChart_upiPaymentCountIsCorrect() {
        savePayment(PaymentMethod.UPI,  PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.UPI,  PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.BANK, PaymentStatus.COMPLETED);

        List<Payment> payments = paymentService.listPayments(user.getId(), null);

        long upiCount  = payments.stream().filter(p -> p.getMethod() == PaymentMethod.UPI).count();
        long bankCount = payments.stream().filter(p -> p.getMethod() == PaymentMethod.BANK).count();

        assertEquals(2, upiCount,  "methodChart: UPI bar must reflect UPI payment count");
        assertEquals(1, bankCount, "methodChart: Bank bar must reflect BANK payment count");
    }

    @Test
    void methodChart_bankPaymentCountIsCorrect() {
        savePayment(PaymentMethod.BANK, PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.BANK, PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.BANK, PaymentStatus.FAILED);

        List<Payment> payments = paymentService.listPayments(user.getId(), null);

        long bankCount = payments.stream().filter(p -> p.getMethod() == PaymentMethod.BANK).count();

        assertEquals(3, bankCount,
                "methodChart: Bank bar must count both COMPLETED and FAILED BANK payments");
    }

    @Test
    void methodChart_returnsZeroCountsWhenNoPayments() {
        List<Payment> payments = paymentService.listPayments(user.getId(), null);

        long upiCount  = payments.stream().filter(p -> p.getMethod() == PaymentMethod.UPI).count();
        long bankCount = payments.stream().filter(p -> p.getMethod() == PaymentMethod.BANK).count();

        assertEquals(0, upiCount,  "methodChart: UPI bar must be 0 when no payments exist");
        assertEquals(0, bankCount, "methodChart: Bank bar must be 0 when no payments exist");
    }

    // ----------------------------------------------------------------
    // volumeChart — line: daily payment counts over last 7 days
    // ----------------------------------------------------------------

    @Test
    void volumeChart_paymentsCreatedTodayAreIncludedInDailyCount() {
        savePayment(PaymentMethod.UPI,  PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.BANK, PaymentStatus.COMPLETED);

        List<Payment> allPayments = paymentService.listPayments(user.getId(), null);

        Instant oneDayAgo = Instant.now().minusSeconds(86400);
        long todayCount = allPayments.stream()
                .filter(p -> p.getCreatedAt().isAfter(oneDayAgo))
                .count();

        assertEquals(2, todayCount,
                "volumeChart: today's data point must include all payments created today");
    }

    @Test
    void volumeChart_totalAcrossSevenDaysMatchesAllPayments() {
        savePayment(PaymentMethod.UPI,  PaymentStatus.COMPLETED);
        savePayment(PaymentMethod.UPI,  PaymentStatus.FAILED);
        savePayment(PaymentMethod.BANK, PaymentStatus.COMPLETED);

        List<Payment> allPayments = paymentService.listPayments(user.getId(), null);

        Instant sevenDaysAgo = Instant.now().minusSeconds(7L * 86400);
        long sevenDayCount = allPayments.stream()
                .filter(p -> p.getCreatedAt().isAfter(sevenDaysAgo))
                .count();

        assertEquals(3, sevenDayCount,
                "volumeChart: sum of all daily bars over 7 days must equal total recent payments");
    }

    @Test
    void volumeChart_returnsZeroCountWhenNoPayments() {
        List<Payment> payments = paymentService.listPayments(user.getId(), null);

        Instant sevenDaysAgo = Instant.now().minusSeconds(7L * 86400);
        long count = payments.stream()
                .filter(p -> p.getCreatedAt().isAfter(sevenDaysAgo))
                .count();

        assertEquals(0, count,
                "volumeChart: all daily bars must be 0 when there are no payments");
    }

    // ----------------------------------------------------------------
    // linkedBankAccounts KPI (feeds kpiAccounts chip on balance card)
    // ----------------------------------------------------------------

    @Test
    void balanceCard_linkedBankAccountsCountIsCorrect() {
        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        assertEquals(1, summary.linkedBankAccounts(),
                "Balance card: linkedBankAccounts must equal the number of linked accounts");
    }

    @Test
    void balanceCard_combinedBalanceReflectsAccountBalance() {
        DashboardSummaryResponse summary = paymentService.getDashboardSummary(user.getId());

        assertEquals(0, new BigDecimal("100000.00").compareTo(summary.combinedBalance()),
                "Balance card: combinedBalance must equal the sum of all account balances");
    }
}

