package org.example.truepay.service;

import org.example.truepay.api.BankAccountRequest;
import org.example.truepay.model.BankAccount;
import org.example.truepay.model.ErrorCode;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.BankAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BankAccountService {
    private final BankAccountRepository bankAccountRepository;
    private final ProfileService profileService;
    private final PasswordEncoder passwordEncoder;

    public BankAccountService(BankAccountRepository bankAccountRepository,
                              ProfileService profileService,
                              PasswordEncoder passwordEncoder) {
        this.bankAccountRepository = bankAccountRepository;
        this.profileService = profileService;
        this.passwordEncoder = passwordEncoder;
    }

    public BankAccount addAccount(Long userId, BankAccountRequest request) {
        UserProfile user = profileService.getUserOrThrow(userId);

        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setBankName(request.bankName());
        account.setAccountNumber(request.accountNumber());
        account.setIfscCode(request.ifscCode().toUpperCase());
        account.setBankPinHash(passwordEncoder.encode(request.bankPin()));
        account.setAccountType("saving");
        account.setBalance(request.openingBalance());

        return bankAccountRepository.save(account);
    }

    public List<BankAccount> listForUser(Long userId) {
        profileService.getUserOrThrow(userId);
        return bankAccountRepository.findByUserId(userId);
    }

    public BigDecimal combinedBalance(Long userId) {
        profileService.getUserOrThrow(userId);
        return bankAccountRepository.getCombinedBalance(userId);
    }

    public BankAccount getForPayment(Long userId, Long accountId) {
        BankAccount account = bankAccountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Source account not found"));

        if (!account.getUser().getId().equals(userId)) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Source account does not belong to user");
        }

        return account;
    }

    public void validateBankPin(BankAccount account, String bankPin) {
        if (!passwordEncoder.matches(bankPin, account.getBankPinHash())) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Invalid bank PIN");
        }
    }

    public void deleteAccount(Long userId, Long accountId) {
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Bank account not found"));

        if (!account.getUser().getId().equals(userId)) {
            throw new TruePayException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST, "Bank account does not belong to user");
        }

        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new TruePayException(ErrorCode.BANK_ACCOUNT_HAS_BALANCE, HttpStatus.BAD_REQUEST,
                    "Transfer or withdraw balance before deleting the account");
        }

        bankAccountRepository.delete(account);
    }
}

