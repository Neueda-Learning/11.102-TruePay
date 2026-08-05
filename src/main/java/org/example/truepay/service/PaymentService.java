package org.example.truepay.service;

import org.example.truepay.api.BankPaymentRequest;
import org.example.truepay.api.DashboardSummaryResponse;
import org.example.truepay.api.ReceiverVerificationResponse;
import org.example.truepay.api.TransactionAuditResponse;
import org.example.truepay.api.UpiPaymentRequest;
import org.example.truepay.model.*;
import org.example.truepay.repository.BankAccountRepository;
import org.example.truepay.repository.FraudAlertRepository;
import org.example.truepay.repository.PaymentRepository;
import org.example.truepay.repository.PaymentStatusHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "INR");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");
    private static final BigDecimal FRAUD_AMOUNT = new BigDecimal("50000.00");

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository statusHistoryRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ProfileService profileService;
    private final BankAccountService bankAccountService;
    private final BeneficiaryService beneficiaryService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentStatusHistoryRepository statusHistoryRepository,
                          FraudAlertRepository fraudAlertRepository,
                          BankAccountRepository bankAccountRepository,
                          ProfileService profileService,
                          BankAccountService bankAccountService,
                          BeneficiaryService beneficiaryService) {
        this.paymentRepository = paymentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.profileService = profileService;
        this.bankAccountService = bankAccountService;
        this.beneficiaryService = beneficiaryService;
    }

    @Transactional
    public Payment createUpiPayment(Long userId, UpiPaymentRequest request) {
        return createAndProcessPayment(
                userId,
                request.sourceAccountId(),
                request.amount(),
                request.currency(),
                request.idempotencyKey(),
                request.appPin(),
                request.bankPin(),
                PaymentMethod.UPI,
                request.destinationUpiId(),
                null,
                null,
                null,
                null
        );
    }

    @Transactional
    public Payment createBankPayment(Long userId, BankPaymentRequest request) {
        Beneficiary beneficiary = null;
        if (request.beneficiaryId() != null) {
            beneficiary = beneficiaryService.getOwnedOrThrow(userId, request.beneficiaryId());
        }

        String destinationAccount = beneficiary != null ? beneficiary.getAccountNumber() : request.destinationAccount();
        String destinationIfsc = beneficiary != null ? beneficiary.getIfscCode() : request.destinationIfsc();
        String receiverName = beneficiary != null ? beneficiary.getName() : request.receiverName();

        return createAndProcessPayment(
                userId,
                request.sourceAccountId(),
                request.amount(),
                request.currency(),
                request.idempotencyKey(),
                request.appPin(),
                request.bankPin(),
                PaymentMethod.BANK,
                null,
                receiverName,
                destinationAccount,
                destinationIfsc,
                request.reference()
        );
    }

    private Payment createAndProcessPayment(Long userId,
                                            Long sourceAccountId,
                                            BigDecimal amount,
                                            String currency,
                                            String idempotencyKey,
                                            String appPin,
                                            String bankPin,
                                            PaymentMethod method,
                                            String destinationUpi,
                                            String receiverName,
                                            String destinationAccount,
                                            String destinationIfsc,
                                            String referenceRemark) {

        Payment existing = paymentRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
        if (existing != null) {
            return existing;
        }

        UserProfile user = profileService.getUserOrThrow(userId);
        profileService.validateAppPin(user, appPin);
        BankAccount source = bankAccountService.getForPayment(userId, sourceAccountId);
        bankAccountService.validateBankPin(source, bankPin);

        validatePaymentFields(source, amount, currency, method, destinationUpi, receiverName, destinationAccount, destinationIfsc);

        Payment payment = new Payment();
        payment.setUser(user);
        payment.setSourceAccount(source);
        payment.setMethod(method);
        payment.setAmount(amount);
        payment.setCurrency(currency.toUpperCase());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setDestinationUpiId(destinationUpi);
        payment.setReceiverName(receiverName);
        payment.setDestinationAccount(destinationAccount);
        payment.setDestinationIfsc(destinationIfsc != null ? destinationIfsc.toUpperCase(Locale.ROOT) : null);
        payment.setReferenceRemark(referenceRemark);
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.save(payment);
        recordStatus(payment, PaymentStatus.CREATED, "API", "Payment submitted");

        payment.setStatus(PaymentStatus.VALIDATED);
        recordStatus(payment, PaymentStatus.VALIDATED, "SYSTEM", "Validation checks passed");

        if (runFraudChecks(payment)) {
            return payment;
        }

        if (source.getBalance().compareTo(amount) < 0) {
            failPayment(payment, ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds in source account");
            return payment;
        }

        payment.setStatus(PaymentStatus.SENT);
        recordStatus(payment, PaymentStatus.SENT, "SYSTEM", "Payment sent to destination simulator");

        source.setBalance(source.getBalance().subtract(amount));
        creditReceiverIfInternal(method, destinationAccount, destinationIfsc, amount);
        payment.setStatus(PaymentStatus.COMPLETED);
        recordStatus(payment, PaymentStatus.COMPLETED, "SYSTEM", "Payment completed successfully");

        return paymentRepository.save(payment);
    }

    private void validatePaymentFields(BankAccount sourceAccount,
                                       BigDecimal amount,
                                       String currency,
                                       PaymentMethod method,
                                       String destinationUpi,
                                       String receiverName,
                                       String destinationAccount,
                                       String destinationIfsc) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new TruePayException(ErrorCode.INVALID_AMOUNT, HttpStatus.BAD_REQUEST, "Amount must be > 0 and <= 1,000,000");
        }

        if (!SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
            throw new TruePayException(ErrorCode.INVALID_CURRENCY, HttpStatus.BAD_REQUEST,
                    "Unsupported currency. Supported: " + SUPPORTED_CURRENCIES);
        }

        if (method == PaymentMethod.UPI && (destinationUpi == null || destinationUpi.isBlank())) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Destination UPI ID is required");
        }

        if (method == PaymentMethod.BANK && (destinationAccount == null || destinationAccount.isBlank() ||
                destinationIfsc == null || destinationIfsc.isBlank())) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Destination account and IFSC are required");
        }

        if (method == PaymentMethod.BANK && (receiverName == null || receiverName.isBlank())) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Receiver name is required");
        }

        if (method == PaymentMethod.BANK && !destinationAccount.matches("^\\d{8,20}$")) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Receiver account number is invalid");
        }

        if (method == PaymentMethod.BANK && !destinationIfsc.toUpperCase(Locale.ROOT).matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Receiver IFSC format is invalid");
        }

        if (method == PaymentMethod.BANK && sourceAccount.getAccountNumber().equals(destinationAccount)
                && sourceAccount.getIfscCode().equalsIgnoreCase(destinationIfsc)) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Sender and receiver accounts must be different");
        }
    }

    private void creditReceiverIfInternal(PaymentMethod method,
                                          String destinationAccount,
                                          String destinationIfsc,
                                          BigDecimal amount) {
        if (method != PaymentMethod.BANK) {
            return;
        }

        bankAccountRepository.findByAccountNumberAndIfscCode(destinationAccount, destinationIfsc.toUpperCase(Locale.ROOT))
                .ifPresent(receiver -> receiver.setBalance(receiver.getBalance().add(amount)));
    }

    private boolean runFraudChecks(Payment payment) {
        long recentPayments = paymentRepository.countByUserIdAndCreatedAtAfter(
                payment.getUser().getId(),
                Instant.now().minus(1, ChronoUnit.MINUTES)
        );

        boolean suspiciousAmount = payment.getAmount().compareTo(FRAUD_AMOUNT) > 0;
        boolean suspiciousFrequency = recentPayments > 3;

        if (suspiciousAmount || suspiciousFrequency) {
            FraudAlert alert = new FraudAlert();
            alert.setPayment(payment);
            alert.setRiskScore(suspiciousAmount ? 90 : 75);
            alert.setReason(suspiciousAmount
                    ? "High-value transaction detected"
                    : "Unusual transaction frequency detected");
            fraudAlertRepository.save(alert);

            failPayment(payment, ErrorCode.SUSPICIOUS_TRANSACTION, alert.getReason());
            return true;
        }

        return false;
    }

    private void failPayment(Payment payment, ErrorCode errorCode, String message) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorCode(errorCode);
        payment.setErrorMessage(message);
        recordStatus(payment, PaymentStatus.FAILED, "SYSTEM", message);
        paymentRepository.save(payment);
    }

    private void recordStatus(Payment payment, PaymentStatus status, String actor, String notes) {
        payment.setStatus(status);

        PaymentStatusHistory history = new PaymentStatusHistory();
        history.setPayment(payment);
        history.setStatus(status);
        history.setTriggeredBy(actor);
        history.setNotes(notes);

        statusHistoryRepository.save(history);
    }

    public Payment getPayment(Long userId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new TruePayException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found"));
        if (!payment.getUser().getId().equals(userId)) {
            throw new TruePayException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found");
        }
        return payment;
    }

    public List<Payment> listPayments(Long userId, PaymentStatus status) {
        if (status == null) {
            return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return paymentRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
    }

    public List<PaymentStatusHistory> getHistory(Long userId, UUID paymentId) {
        getPayment(userId, paymentId);
        return statusHistoryRepository.findByPaymentIdOrderByChangedAtAsc(paymentId);
    }

    @Transactional(readOnly = true)
    public List<TransactionAuditResponse> getAuditHistory(Long userId) {
        return statusHistoryRepository.findAuditHistoryByUserIdOrderByChangedAtDesc(userId).stream()
                .map(history -> {
                    Payment payment = history.getPayment();
                    return new TransactionAuditResponse(
                            payment.getId(),
                            payment.getMethod(),
                            payment.getAmount(),
                            payment.getCurrency(),
                            resolveReceiver(payment),
                            history.getStatus(),
                            history.getTriggeredBy(),
                            history.getChangedAt(),
                            history.getNotes(),
                            payment.getReferenceRemark()
                    );
                })
                .toList();
    }

    public DashboardSummaryResponse getDashboardSummary(Long userId) {
        return new DashboardSummaryResponse(
                bankAccountService.combinedBalance(userId),
                bankAccountService.listForUser(userId).size(),
                paymentRepository.countByUserId(userId),
                paymentRepository.countByUserIdAndStatus(userId, PaymentStatus.COMPLETED),
                paymentRepository.countByUserIdAndStatus(userId, PaymentStatus.FAILED),
                fraudAlertRepository.countByPaymentUserId(userId)
        );
    }

    public ReceiverVerificationResponse verifyReceiver(String accountNumber, String ifscCode) {
        if (!accountNumber.matches("^\\d{8,12}$")) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Account number must be 8-12 digits");
        }

        String normalizedIfsc = ifscCode.toUpperCase(Locale.ROOT);
        if (!normalizedIfsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Receiver IFSC format is invalid");
        }

        return bankAccountRepository.findByAccountNumberAndIfscCode(accountNumber, normalizedIfsc)
                .map(account -> new ReceiverVerificationResponse(true, account.getUser().getFullName(), "Receiver verified in TruePay"))
                .orElseGet(() -> new ReceiverVerificationResponse(false, "External account", "Receiver not found in TruePay, transfer will be simulated"));
    }

    private String resolveReceiver(Payment payment) {
        if (payment.getReceiverName() != null && !payment.getReceiverName().isBlank()) {
            return payment.getReceiverName();
        }
        if (payment.getDestinationUpiId() != null && !payment.getDestinationUpiId().isBlank()) {
            return payment.getDestinationUpiId();
        }
        if (payment.getDestinationAccount() != null && !payment.getDestinationAccount().isBlank()) {
            return payment.getDestinationAccount();
        }
        return "-";
    }
}


