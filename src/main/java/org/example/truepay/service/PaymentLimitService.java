package org.example.truepay.service;

import org.example.truepay.api.PaymentLimitRequest;
import org.example.truepay.api.PaymentLimitResponse;
import org.example.truepay.model.ErrorCode;
import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentLimit;
import org.example.truepay.model.PaymentMethod;
import org.example.truepay.model.PaymentStatus;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.PaymentLimitRepository;
import org.example.truepay.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class PaymentLimitService {
    private final PaymentLimitRepository paymentLimitRepository;
    private final PaymentRepository paymentRepository;
    private final ProfileService profileService;

    public PaymentLimitService(PaymentLimitRepository paymentLimitRepository,
                               PaymentRepository paymentRepository,
                               ProfileService profileService) {
        this.paymentLimitRepository = paymentLimitRepository;
        this.paymentRepository = paymentRepository;
        this.profileService = profileService;
    }

    @Transactional
    public PaymentLimitResponse getLimits(Long userId) {
        PaymentLimit limits = getOrCreate(userId);
        return toResponse(limits);
    }

    @Transactional
    public PaymentLimitResponse updateLimits(Long userId, PaymentLimitRequest request) {
        PaymentLimit limits = getOrCreate(userId);

        limits.setDailyEnabled(Boolean.TRUE.equals(request.dailyEnabled()));
        limits.setMonthlyEnabled(Boolean.TRUE.equals(request.monthlyEnabled()));
        limits.setPerTransactionEnabled(Boolean.TRUE.equals(request.perTransactionEnabled()));

        limits.setDailyLimit(normalizeLimitValue(request.dailyLimit(), limits.isDailyEnabled(), "Daily"));
        limits.setMonthlyLimit(normalizeLimitValue(request.monthlyLimit(), limits.isMonthlyEnabled(), "Monthly"));
        limits.setPerTransactionLimit(normalizeLimitValue(request.perTransactionLimit(), limits.isPerTransactionEnabled(), "Per-transaction"));

        return toResponse(paymentLimitRepository.save(limits));
    }

    @Transactional(readOnly = true)
    public void validateWithinLimits(Payment paymentDraft) {
        PaymentLimit limits = paymentLimitRepository.findByUserId(paymentDraft.getUser().getId()).orElse(null);
        if (limits == null) {
            return;
        }

        BigDecimal amount = paymentDraft.getAmount();
        if (amount == null) {
            return;
        }

        if (!isSupportedTransferMethod(paymentDraft.getMethod())) {
            return;
        }

        if (limits.isPerTransactionEnabled() && amount.compareTo(requireLimit(limits.getPerTransactionLimit(), "Per-transaction")) > 0) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Per-transaction transfer limit exceeded");
        }

        Instant now = Instant.now();
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant monthStart = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        if (limits.isDailyEnabled()) {
            BigDecimal spentToday = getSuccessfulAmountSince(paymentDraft.getUser().getId(), dayStart);
            if (spentToday.add(amount).compareTo(requireLimit(limits.getDailyLimit(), "Daily")) > 0) {
                throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                        "Daily transfer limit exceeded");
            }
        }

        if (limits.isMonthlyEnabled()) {
            BigDecimal spentThisMonth = getSuccessfulAmountSince(paymentDraft.getUser().getId(), monthStart);
            if (spentThisMonth.add(amount).compareTo(requireLimit(limits.getMonthlyLimit(), "Monthly")) > 0) {
                throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                        "Monthly transfer limit exceeded");
            }
        }
    }

    private PaymentLimit getOrCreate(Long userId) {
        return paymentLimitRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfile user = profileService.getUserOrThrow(userId);
                    PaymentLimit limits = new PaymentLimit();
                    limits.setUser(user);
                    limits.setDailyEnabled(false);
                    limits.setMonthlyEnabled(false);
                    limits.setPerTransactionEnabled(false);
                    return paymentLimitRepository.save(limits);
                });
    }

    private BigDecimal getSuccessfulAmountSince(Long userId, Instant fromInclusive) {
        BigDecimal total = paymentRepository.sumSuccessfulAmountsByUserAndCreatedAtAfter(
                userId,
                PaymentStatus.SUCCESS,
                List.of(PaymentMethod.UPI, PaymentMethod.BANK_TRANSFER, PaymentMethod.BANK),
                fromInclusive
        );
        return total == null ? BigDecimal.ZERO : total;
    }

    private BigDecimal normalizeLimitValue(BigDecimal rawValue, boolean enabled, String label) {
        if (!enabled) {
            return null;
        }
        if (rawValue == null || rawValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    label + " limit must be greater than zero when enabled");
        }
        return rawValue;
    }

    private BigDecimal requireLimit(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    label + " limit is not configured correctly");
        }
        return value;
    }

    private boolean isSupportedTransferMethod(PaymentMethod method) {
        return method == PaymentMethod.UPI || method == PaymentMethod.BANK_TRANSFER || method == PaymentMethod.BANK;
    }

    private PaymentLimitResponse toResponse(PaymentLimit limits) {
        return new PaymentLimitResponse(
                limits.getUser().getId(),
                limits.isDailyEnabled(),
                limits.getDailyLimit(),
                limits.isMonthlyEnabled(),
                limits.getMonthlyLimit(),
                limits.isPerTransactionEnabled(),
                limits.getPerTransactionLimit()
        );
    }
}


