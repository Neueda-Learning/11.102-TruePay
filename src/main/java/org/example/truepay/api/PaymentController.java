package org.example.truepay.api;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.example.truepay.model.Payment;
import org.example.truepay.model.PaymentStatus;
import org.example.truepay.service.PaymentService;
import org.example.truepay.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final SessionService sessionService;

    public PaymentController(PaymentService paymentService, SessionService sessionService) {
        this.paymentService = paymentService;
        this.sessionService = sessionService;
    }

    @PostMapping("/pay-to-upi")
    public PaymentResponse payToUpi(@Valid @RequestBody UpiPaymentRequest request, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return toResponse(paymentService.createUpiPayment(userId, request));
    }

    @PostMapping("/pay-to-bank")
    public PaymentResponse payToBank(@Valid @RequestBody BankPaymentRequest request, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return toResponse(paymentService.createBankPayment(userId, request));
    }

    @PostMapping("/{paymentId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public PaymentResponse cancelPayment(@PathVariable UUID paymentId,
                                         @RequestParam(required = false) String reason,
                                         HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return toResponse(paymentService.cancelPayment(userId, paymentId, reason));
    }

    @GetMapping("/audits")
    public List<TransactionAuditResponse> listAudits(HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return paymentService.getAuditHistory(userId);
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@PathVariable UUID paymentId, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return toResponse(paymentService.getPayment(userId, paymentId));
    }

    @GetMapping
    public List<PaymentResponse> listPayments(HttpSession session,
                                              @RequestParam(required = false) PaymentStatus status) {
        Long userId = sessionService.requireUserId(session);
        return paymentService.listPayments(userId, status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{paymentId}/history")
    public List<StatusHistoryResponse> history(@PathVariable UUID paymentId, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return paymentService.getHistory(userId, paymentId).stream()
                .map(h -> new StatusHistoryResponse(h.getStatus(), h.getTriggeredBy(), h.getChangedAt(), h.getNotes()))
                .toList();
    }

    @GetMapping("/verify-receiver")
    public ReceiverVerificationResponse verifyReceiver(@RequestParam String accountNumber,
                                                       @RequestParam String ifscCode,
                                                       HttpSession session) {
        sessionService.requireUserId(session);
        return paymentService.verifyReceiver(accountNumber, ifscCode);
    }

    private PaymentResponse toResponse(Payment payment) {
        String message = payment.getStatus() == PaymentStatus.SUCCESS
                ? "Payment completed successfully"
                : payment.getStatus() == PaymentStatus.CANCELLED
                ? "Payment cancelled"
                : payment.getStatus() == PaymentStatus.FAILED
                ? "Payment failed"
                : "Payment pending";

        return new PaymentResponse(
                payment.getId(),
                payment.getId().toString(),
                payment.getUser().getId(),
                payment.getSourceAccount() != null ? payment.getSourceAccount().getId() : null,
                payment.getMethod(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                message,
                payment.getFailureReason(),
                payment.getErrorCode(),
                payment.getErrorMessage(),
                payment.getDestinationUpiId(),
                payment.getDestinationAccount(),
                payment.getDestinationIfsc(),
                payment.getReceiverName(),
                payment.getReferenceRemark(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}

