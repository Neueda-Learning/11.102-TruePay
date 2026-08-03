package org.example.truepay.api;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        BigDecimal combinedBalance,
        int linkedBankAccounts,
        long totalPayments,
        long completedPayments,
        long failedPayments,
        long fraudAlerts
) {
}

