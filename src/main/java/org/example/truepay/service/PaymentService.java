package org.example.truepay.service;

import org.example.truepay.api.*;
import org.example.truepay.model.*;
import org.example.truepay.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "INR");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");
    private static final BigDecimal FRAUD_AMOUNT = new BigDecimal("50000.00");

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository statusHistoryRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuditLogRepository auditLogRepository;
    private final ProfileService profileService;
    private final BankAccountService bankAccountService;
    private final BeneficiaryService beneficiaryService;
    private final PaymentLimitService paymentLimitService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentStatusHistoryRepository statusHistoryRepository,
                          FraudAlertRepository fraudAlertRepository,
                          BankAccountRepository bankAccountRepository,
                          UserProfileRepository userProfileRepository,
                          AuditLogRepository auditLogRepository,
                          ProfileService profileService,
                          BankAccountService bankAccountService,
                          BeneficiaryService beneficiaryService,
                          PaymentLimitService paymentLimitService) {
        this.paymentRepository = paymentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.auditLogRepository = auditLogRepository;
        this.profileService = profileService;
        this.bankAccountService = bankAccountService;
        this.beneficiaryService = beneficiaryService;
        this.paymentLimitService = paymentLimitService;
    }

    @Transactional
    public Payment createUpiPayment(Long userId, UpiPaymentRequest request) {
        ReceiverType receiverType = request.destinationUpiId() != null && request.destinationUpiId().endsWith("@mobile")
                ? ReceiverType.MOBILE_NUMBER
                : ReceiverType.UPI_ID;
        return processUpiPayment(userId, request.sourceAccountId(), request.destinationUpiId(), receiverType,
                request.amount(), request.currency(), request.bankPin());
    }

    @Transactional
    public Payment createUpiPayment(Long userId, UpiTransferRequest request) {
        ReceiverType receiverType = parseUpiReceiverType(request.receiverType());
        return processUpiPayment(userId, resolveSourceAccountId(userId, request.sourceAccount()), request.receiver(), receiverType,
                request.amount(), request.currency(), request.bankPin());
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

        return processBankTransfer(userId,
                request.sourceAccountId(),
                destinationAccount,
                destinationIfsc,
                receiverName,
                request.amount(),
                request.currency(),
                request.bankPin(),
                request.reference());
    }

    @Transactional
    public Payment createBankPayment(Long userId, BankTransferRequest request) {
        Long sourceAccountId = resolveSourceAccountId(userId, request.sourceAccount());
        BankAccount destination = bankAccountRepository.findByAccountNumber(request.destinationAccount()).orElse(null);
        String ifsc = destination != null ? destination.getIfscCode() : null;
        String receiverName = destination != null ? destination.getUser().getFullName() : null;
        return processBankTransfer(userId,
                sourceAccountId,
                request.destinationAccount(),
                ifsc,
                receiverName,
                request.amount(),
                request.currency(),
                request.bankPin(),
                null);
    }

    private Payment processUpiPayment(Long userId,
                                      Long sourceAccountId,
                                      String receiver,
                                      ReceiverType receiverType,
                                      BigDecimal amount,
                                      String currency,
                                      String bankPin) {
        return createAndProcessPayment(userId, sourceAccountId, amount, currency, bankPin,
                PaymentMethod.UPI, receiverType, receiver, null, null, null, null);
    }

    private Payment processBankTransfer(Long userId,
                                        Long sourceAccountId,
                                        String destinationAccount,
                                        String destinationIfsc,
                                        String receiverName,
                                        BigDecimal amount,
                                        String currency,
                                        String bankPin,
                                        String referenceRemark) {
        return createAndProcessPayment(userId, sourceAccountId, amount, currency, bankPin,
                PaymentMethod.BANK_TRANSFER, ReceiverType.BANK_ACCOUNT, null,
                receiverName, destinationAccount, destinationIfsc, referenceRemark);
    }

    private Payment createAndProcessPayment(Long userId,
                                            Long sourceAccountId,
                                            BigDecimal amount,
                                            String currency,
                                            String bankPin,
                                            PaymentMethod method,
                                            ReceiverType receiverType,
                                            String receiverValue,
                                            String receiverName,
                                            String destinationAccount,
                                            String destinationIfsc,
                                            String referenceRemark) {
        UserProfile user = profileService.getUserOrThrow(userId);
        BankAccount source = bankAccountRepository.findByIdForUpdate(sourceAccountId).orElse(null);
        if (source == null || !source.getUser().getId().equals(userId)) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Source account not found");
        }

        Payment paymentDraft = new Payment();
        paymentDraft.setUser(user);
        paymentDraft.setMethod(method);
        paymentDraft.setAmount(amount);
        paymentLimitService.validateWithinLimits(paymentDraft);

        Payment payment = createPendingPayment(user, source, amount, currency, method, receiverType,
                receiverValue, receiverName, destinationAccount, destinationIfsc, referenceRemark);

        try {
            bankAccountService.validateBankPin(source, bankPin);
            validatePaymentFields(source, amount, currency, method, receiverType, receiverValue, destinationAccount, destinationIfsc);

            if (runFraudChecks(payment)) {
                return payment;
            }

            if (source.getBalance().compareTo(amount) < 0) {
                failPayment(payment, ErrorCode.INSUFFICIENT_FUNDS, "Insufficient balance");
                return payment;
            }

            if (method == PaymentMethod.UPI) {
                source.setBalance(source.getBalance().subtract(amount));

                payment.setReceiverName(receiverValue);
                payment.setDestinationAccount(null);
                payment.setDestinationIfsc(null);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setErrorCode(null);
                payment.setErrorMessage(null);
                payment.setFailureReason(null);
                paymentRepository.save(payment);
                recordStatus(payment, PaymentStatus.SUCCESS, "SYSTEM", "Payment completed successfully");
                safeRecordAudit(payment, "PAYMENT_SUCCESS", "Payment of " + payment.getCurrency() + " " + payment.getAmount() + " completed");
                return payment;
            }

            BankAccount destination = resolveDestinationAccount(payment, receiverType, receiverValue, destinationAccount, destinationIfsc);
            if (destination == null) {
                failPayment(payment, ErrorCode.INVALID_ACCOUNT, "Invalid destination account");
                return payment;
            }

            source.setBalance(source.getBalance().subtract(amount));
            destination.setBalance(destination.getBalance().add(amount));

            payment.setReceiverName(destination.getUser().getFullName());
            payment.setDestinationAccount(destination.getAccountNumber());
            payment.setDestinationIfsc(destination.getIfscCode());
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setErrorCode(null);
            payment.setErrorMessage(null);
            payment.setFailureReason(null);
            paymentRepository.save(payment);
            recordStatus(payment, PaymentStatus.SUCCESS, "SYSTEM", "Payment completed successfully");
            safeRecordAudit(payment, "PAYMENT_SUCCESS", "Payment of " + payment.getCurrency() + " " + payment.getAmount() + " completed");
            return payment;
        } catch (TruePayException ex) {
            failPayment(payment, ex.getErrorCode(), ex.getMessage());
            return payment;
        } catch (Exception ex) {
            failPayment(payment, ErrorCode.PROCESSING_ERROR, ex.getMessage());
            return payment;
        }
    }

    private Payment createPendingPayment(UserProfile user,
                                         BankAccount sourceAccount,
                                         BigDecimal amount,
                                         String currency,
                                         PaymentMethod method,
                                         ReceiverType receiverType,
                                         String receiverValue,
                                         String receiverName,
                                         String destinationAccount,
                                         String destinationIfsc,
                                         String referenceRemark) {
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setSourceAccount(sourceAccount);
        payment.setMethod(method);
        payment.setReceiverType(receiverType);
        payment.setAmount(amount);
        payment.setCurrency(currency != null ? currency.toUpperCase(Locale.ROOT) : null);
        if (receiverType == ReceiverType.BANK_ACCOUNT) {
            payment.setDestinationAccount(destinationAccount);
            payment.setDestinationIfsc(destinationIfsc != null ? destinationIfsc.toUpperCase(Locale.ROOT) : null);
        } else {
            payment.setDestinationUpiId(receiverValue);
        }
        payment.setReceiverName(receiverName);
        payment.setReferenceRemark(referenceRemark);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);
        recordStatus(payment, PaymentStatus.PENDING, "API", "Payment submitted");
        safeRecordAudit(payment, "PAYMENT_INITIATED", "Payment initiated");
        return payment;
    }

    private void validatePaymentFields(BankAccount sourceAccount,
                                       BigDecimal amount,
                                       String currency,
                                       PaymentMethod method,
                                       ReceiverType receiverType,
                                       String receiverValue,
                                       String destinationAccount,
                                       String destinationIfsc) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new TruePayException(ErrorCode.INVALID_AMOUNT, HttpStatus.BAD_REQUEST, "Invalid payment amount");
        }
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency.toUpperCase(Locale.ROOT))) {
            throw new TruePayException(ErrorCode.INVALID_CURRENCY, HttpStatus.BAD_REQUEST,
                    "Unsupported currency. Supported: " + SUPPORTED_CURRENCIES);
        }

        if (method == PaymentMethod.UPI) {
            if (receiverType == ReceiverType.MOBILE_NUMBER) {
                if (receiverValue == null || !receiverValue.matches("^\\d{10}$")) {
                    throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Receiver mobile number is invalid");
                }
            } else if (receiverValue == null || receiverValue.isBlank()) {
                throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Receiver UPI ID is required");
            }
            return;
        }

        if (destinationAccount == null || !destinationAccount.matches("^\\d{8,20}$")) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Receiver account number is invalid");
        }
        if (destinationIfsc == null || !destinationIfsc.toUpperCase(Locale.ROOT).matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Receiver IFSC format is invalid");
        }
        if (sourceAccount.getAccountNumber().equals(destinationAccount)
                && sourceAccount.getIfscCode().equalsIgnoreCase(destinationIfsc)) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Sender and receiver accounts must be different");
        }
    }

    private BankAccount resolveDestinationAccount(Payment payment,
                                                  ReceiverType receiverType,
                                                  String receiverValue,
                                                  String destinationAccount,
                                                  String destinationIfsc) {
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER || payment.getMethod() == PaymentMethod.BANK) {
            return bankAccountRepository.findByAccountNumberAndIfscCode(destinationAccount, destinationIfsc.toUpperCase(Locale.ROOT)).orElse(null);
        }

        UserProfile receiverUser = null;
        if (receiverType == ReceiverType.MOBILE_NUMBER) {
            receiverUser = userProfileRepository.findByMobile(receiverValue).orElse(null);
        } else if (receiverType == ReceiverType.UPI_ID) {
            receiverUser = findUserByUpiId(receiverValue);
        }

        if (receiverUser == null) {
            return null;
        }
        return bankAccountRepository.findFirstByUserIdOrderByIdAsc(receiverUser.getId()).orElse(null);
    }

    private UserProfile findUserByUpiId(String upiId) {
        if (upiId == null || upiId.isBlank() || !upiId.contains("@")) {
            return null;
        }
        String localPart = upiId.substring(0, upiId.indexOf('@')).toLowerCase(Locale.ROOT);
        return userProfileRepository.findAll().stream()
                .filter(user -> {
                    String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase(Locale.ROOT);
                    String emailLocal = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
                    return emailLocal.equals(localPart);
                })
                .findFirst()
                .orElse(null);
    }

    private boolean runFraudChecks(Payment payment) {
        long recentPayments = paymentRepository.countByUserIdAndCreatedAtAfter(
                payment.getUser().getId(),
                Instant.now().minus(1, ChronoUnit.MINUTES)
        );

        boolean suspiciousAmount = payment.getAmount().compareTo(FRAUD_AMOUNT) > 0;
        boolean suspiciousFrequency = recentPayments > 3;
        if (!suspiciousAmount && !suspiciousFrequency) {
            return false;
        }

        FraudAlert alert = new FraudAlert();
        alert.setPayment(payment);
        alert.setRiskScore(suspiciousAmount ? 90 : 75);
        alert.setReason(suspiciousAmount ? "High-value transaction detected" : "Unusual transaction frequency detected");
        fraudAlertRepository.save(alert);
        failPayment(payment, ErrorCode.SUSPICIOUS_TRANSACTION, alert.getReason());
        return true;
    }

    private void failPayment(Payment payment, ErrorCode errorCode, String message) {
        String reason = message == null || message.isBlank() ? "Payment processing failed" : message;
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorCode(errorCode);
        payment.setErrorMessage(reason);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);
        recordStatus(payment, PaymentStatus.FAILED, "SYSTEM", reason);
        safeRecordAudit(payment, "PAYMENT_FAILED", reason);
    }

    @Transactional
    public Payment cancelPayment(Long userId, UUID paymentId, String reason) {
        Payment payment = getPayment(userId, paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new TruePayException(ErrorCode.INVALID_STATUS_TRANSITION, HttpStatus.CONFLICT,
                    "Only pending transactions can be cancelled");
        }

        String cancellationReason = (reason == null || reason.isBlank()) ? "Cancelled by user" : reason.trim();
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setFailureReason(cancellationReason);
        payment.setErrorCode(null);
        payment.setErrorMessage(cancellationReason);
        paymentRepository.save(payment);
        recordStatus(payment, PaymentStatus.CANCELLED, "USER", cancellationReason);
        safeRecordAudit(payment, "PAYMENT_CANCELLED", cancellationReason);
        return payment;
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

    private void recordAudit(Payment payment, String action, String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(payment.getUser());
        auditLog.setTransactionId(payment.getTransactionId());
        auditLog.setAction(action);
        auditLog.setDescription(description);
        auditLogRepository.save(auditLog);
    }

    private void safeRecordAudit(Payment payment, String action, String description) {
        try {
            recordAudit(payment, action, description);
        } catch (Exception ex) {
            log.warn("Audit log write failed for payment {} action {}: {}", payment.getId(), action, ex.getMessage());
        }
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

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getPaymentHistory(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(payment -> new PaymentHistoryResponse(
                        payment.getTransactionId(),
                        payment.getCreatedAt(),
                        payment.getMethod(),
                        payment.getSourceAccount() != null ? maskAccount(payment.getSourceAccount().getAccountNumber()) : "-",
                        resolveReceiver(payment),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getStatus(),
                        payment.getFailureReason()
                ))
                .toList();
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

    @Transactional(readOnly = true)
    public List<AuditHistoryResponse> getAuditHistoryRecords(Long userId) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId).stream()
                .map(log -> new AuditHistoryResponse(
                        log.getId(),
                        log.getUser().getId(),
                        log.getTransactionId(),
                        log.getAction(),
                        log.getDescription(),
                        log.getTimestamp()
                ))
                .toList();
    }

    public DashboardSummaryResponse getDashboardSummary(Long userId) {
        return new DashboardSummaryResponse(
                bankAccountService.combinedBalance(userId),
                bankAccountService.listForUser(userId).size(),
                paymentRepository.countByUserId(userId),
                paymentRepository.countByUserIdAndStatus(userId, PaymentStatus.SUCCESS),
                paymentRepository.countByUserIdAndStatus(userId, PaymentStatus.FAILED),
                fraudAlertRepository.countByPaymentUserId(userId)
        );
    }

    public ReceiverVerificationResponse verifyReceiver(String accountNumber, String ifscCode) {
        if (!accountNumber.matches("^\\d{8,20}$")) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Account number must be 8-20 digits");
        }

        String normalizedIfsc = ifscCode.toUpperCase(Locale.ROOT);
        if (!normalizedIfsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Receiver IFSC format is invalid");
        }

        return bankAccountRepository.findByAccountNumberAndIfscCode(accountNumber, normalizedIfsc)
                .map(account -> new ReceiverVerificationResponse(true, account.getUser().getFullName(), "Receiver verified in TruePay"))
                .orElseGet(() -> new ReceiverVerificationResponse(false, "Unknown receiver", "Receiver not found in TruePay"));
    }

    private ReceiverType parseUpiReceiverType(String receiverType) {
        if (receiverType == null) {
            return ReceiverType.UPI_ID;
        }
        return switch (receiverType.trim().toUpperCase(Locale.ROOT)) {
            case "UPI", "UPI_ID" -> ReceiverType.UPI_ID;
            case "MOBILE", "MOBILE_NUMBER" -> ReceiverType.MOBILE_NUMBER;
            default -> throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Invalid receiver type");
        };
    }

    private Long resolveSourceAccountId(Long userId, String sourceAccountNumber) {
        if (sourceAccountNumber == null || sourceAccountNumber.isBlank()) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Source account not found");
        }
        BankAccount account = bankAccountRepository.findByAccountNumber(sourceAccountNumber)
                .orElseThrow(() -> new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Source account not found"));
        if (!account.getUser().getId().equals(userId)) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Source account not found");
        }
        return account.getId();
    }

    private String resolveReceiver(Payment payment) {
        if (payment.getReceiverName() != null && !payment.getReceiverName().isBlank()) {
            return payment.getReceiverName();
        }
        if (payment.getReceiverType() == ReceiverType.BANK_ACCOUNT) {
            return payment.getDestinationAccount() != null ? maskAccount(payment.getDestinationAccount()) : "-";
        }
        if (payment.getDestinationUpiId() != null && !payment.getDestinationUpiId().isBlank()) {
            return payment.getDestinationUpiId();
        }
        return "-";
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "-";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}

