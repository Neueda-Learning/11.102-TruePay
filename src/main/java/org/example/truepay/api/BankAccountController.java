package org.example.truepay.api;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.example.truepay.model.BankAccount;
import org.example.truepay.service.BankAccountService;
import org.example.truepay.service.SessionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bank-accounts")
public class BankAccountController {
    private final BankAccountService bankAccountService;
    private final SessionService sessionService;

    public BankAccountController(BankAccountService bankAccountService, SessionService sessionService) {
        this.bankAccountService = bankAccountService;
        this.sessionService = sessionService;
    }

    @PostMapping
    public BankAccountResponse addAccount(@Valid @RequestBody BankAccountRequest request, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        BankAccount account = bankAccountService.addAccount(userId, request);
        return toResponse(account);
    }

    @GetMapping
    public List<BankAccountResponse> listAccounts(HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return bankAccountService.listForUser(userId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/combined-balance")
    public BigDecimal combinedBalance(HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        return bankAccountService.combinedBalance(userId);
    }

    @DeleteMapping("/{accountId}")
    public void deleteAccount(@PathVariable Long accountId, HttpSession session) {
        Long userId = sessionService.requireUserId(session);
        bankAccountService.deleteAccount(userId, accountId);
    }

    private BankAccountResponse toResponse(BankAccount account) {
        return new BankAccountResponse(
                account.getId(),
                account.getBankName(),
                account.getAccountNumber(),
                account.getIfscCode(),
                account.getBalance()
        );
    }
}

