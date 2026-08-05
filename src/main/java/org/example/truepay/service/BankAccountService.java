package org.example.truepay.service;

import org.example.truepay.api.BankAccountRequest;
import org.example.truepay.model.BankAccount;
import org.example.truepay.model.ErrorCode;
import org.example.truepay.model.UserProfile;
import org.example.truepay.repository.BankAccountRepository;
import org.example.truepay.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;

@Service
public class BankAccountService {
    private final BankAccountRepository bankAccountRepository;
    private final PaymentRepository paymentRepository;
    private final ProfileService profileService;
    private final PasswordEncoder passwordEncoder;

    public BankAccountService(BankAccountRepository bankAccountRepository,
                              PaymentRepository paymentRepository,
                              ProfileService profileService,
                              PasswordEncoder passwordEncoder) {
        this.bankAccountRepository = bankAccountRepository;
        this.paymentRepository = paymentRepository;
        this.profileService = profileService;
        this.passwordEncoder = passwordEncoder;
    }

    public BankAccount addAccount(Long userId, BankAccountRequest request) {
        UserProfile user = profileService.getUserOrThrow(userId);
        String accountNumber = request.accountNumber().trim();

        if (bankAccountRepository.findByAccountNumber(accountNumber).isPresent()) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Bank account with this account number already exists");
        }

        String accountHolderName = user.getFullName();
        if (accountHolderName == null || accountHolderName.isBlank()) {
            accountHolderName = "TruePay User";
        } else {
            accountHolderName = accountHolderName.trim();
        }

        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setAccountHolderName(accountHolderName);
        account.setBankName(request.bankName().trim());
        account.setAccountNumber(accountNumber);
        account.setIfscCode(request.ifscCode().trim().toUpperCase(Locale.ROOT));
        account.setBankPinHash(passwordEncoder.encode(request.bankPin()));
        account.setAccountType(request.accountType().trim().toUpperCase(Locale.ROOT));
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
        if (bankPin == null || bankPin.isBlank() || !passwordEncoder.matches(bankPin, account.getBankPinHash())) {
            throw new TruePayException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Invalid bank PIN");
        }
    }

    @Transactional
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

        // Preserve payment history rows by detaching source-account references before deleting the account.
        paymentRepository.clearSourceAccountReferences(accountId);
        bankAccountRepository.delete(account);
    }
}

