package org.example.truepay.api;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.example.truepay.service.PaymentLimitService;
import org.example.truepay.service.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-limits")
public class PaymentLimitController {
    private final PaymentLimitService paymentLimitService;
    private final SessionService sessionService;

    public PaymentLimitController(PaymentLimitService paymentLimitService, SessionService sessionService) {
        this.paymentLimitService = paymentLimitService;
        this.sessionService = sessionService;
    }

    @GetMapping
    public PaymentLimitResponse getLimits(HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return paymentLimitService.getLimits(userId);
    }

    @PutMapping
    public PaymentLimitResponse updateLimits(@Valid @RequestBody PaymentLimitRequest request, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return paymentLimitService.updateLimits(userId, request);
    }
}

