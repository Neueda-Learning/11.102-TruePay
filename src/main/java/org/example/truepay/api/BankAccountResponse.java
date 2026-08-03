package org.example.truepay.api;

import java.math.BigDecimal;

public record BankAccountResponse(
        Long id,
        String bankName,
        String accountNumber,
        String ifscCode,
        BigDecimal balance
) {
}

