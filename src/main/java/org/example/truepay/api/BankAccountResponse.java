package org.example.truepay.api;

import java.math.BigDecimal;

public record BankAccountResponse(
        Long id,
        String accountHolderName,
        String bankName,
        String accountNumber,
        String ifscCode,
        String accountType,
        BigDecimal balance
) {
}

