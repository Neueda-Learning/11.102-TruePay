package org.example.truepay.api;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.example.truepay.model.Beneficiary;
import org.example.truepay.service.BeneficiaryService;
import org.example.truepay.service.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {
    private final BeneficiaryService beneficiaryService;
    private final SessionService sessionService;

    public BeneficiaryController(BeneficiaryService beneficiaryService, SessionService sessionService) {
        this.beneficiaryService = beneficiaryService;
        this.sessionService = sessionService;
    }

    @PostMapping
    public BeneficiaryResponse create(@Valid @RequestBody BeneficiaryRequest request, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return toResponse(beneficiaryService.create(userId, request));
    }

    @GetMapping
    public List<BeneficiaryResponse> list(HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return beneficiaryService.list(userId).stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/{beneficiaryId}")
    public void delete(@PathVariable Long beneficiaryId, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        beneficiaryService.delete(userId, beneficiaryId);
    }

    private BeneficiaryResponse toResponse(Beneficiary beneficiary) {
        return new BeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getName(),
                beneficiary.getAccountNumber(),
                beneficiary.getIfscCode()
        );
    }
}

