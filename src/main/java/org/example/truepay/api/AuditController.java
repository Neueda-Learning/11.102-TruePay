package org.example.truepay.api;

import jakarta.servlet.http.HttpSession;
import org.example.truepay.model.ErrorCode;
import org.example.truepay.service.PaymentService;
import org.example.truepay.service.SessionService;
import org.example.truepay.service.TruePayException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/audit", "/audit"})
public class AuditController {
    private final PaymentService paymentService;
    private final SessionService sessionService;

    public AuditController(PaymentService paymentService, SessionService sessionService) {
        this.paymentService = paymentService;
        this.sessionService = sessionService;
    }

    @GetMapping("/history/{userId}")
    public List<AuditHistoryResponse> history(@PathVariable Long userId, HttpSession session) {
        Long sessionUserId = sessionService.requireUserId(session);
        if (!userId.equals(sessionUserId)) {
            throw new TruePayException(ErrorCode.UNAUTHORIZED, HttpStatus.FORBIDDEN, "User mismatch");
        }
        return paymentService.getAuditHistoryRecords(userId);
    }
}

