package org.example.truepay.api;

import jakarta.servlet.http.HttpSession;
import org.example.truepay.service.PaymentService;
import org.example.truepay.service.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final PaymentService paymentService;
    private final SessionService sessionService;

    public DashboardController(PaymentService paymentService, SessionService sessionService) {
        this.paymentService = paymentService;
        this.sessionService = sessionService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return paymentService.getDashboardSummary(userId);
    }
}

