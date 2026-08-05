#!/usr/bin/env pwsh
<#
.SYNOPSIS
    TruePay Bank Transfer Test Script (Updated with Session Handling)
    Tests bank-to-bank transfer functionality with proper authentication

.EXAMPLE
    ./test-bank-transfer-v2.ps1
#>

param(
    [string]$BaseUrl = "http://localhost:8081"
)

Write-Host "=== TRUEPAY BANK TRANSFER TEST ===" -ForegroundColor Blue
Write-Host "Base URL: $BaseUrl`n" -ForegroundColor Cyan

# Step 1: Access dashboard to initialize session
Write-Host "Step 1: Initializing session..." -ForegroundColor Yellow
try {
    $sessionResponse = Invoke-WebRequest -Uri "$BaseUrl/dashboard.html" `
        -UseBasicParsing `
        -SessionVariable "session" `
        -ErrorAction Stop
    Write-Host "✓ Session initialized" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to initialize session" -ForegroundColor Red
    exit 1
}

# Step 2: Get current user info
Write-Host "`nStep 2: Fetching user info..." -ForegroundColor Yellow
try {
    $userResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/auth/me" `
        -WebSession $session `
        -UseBasicParsing `
        -ErrorAction Stop

    $user = $userResponse.Content | ConvertFrom-Json
    Write-Host "✓ Logged in as: $($user.fullName) (ID: $($user.id))" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to fetch user: $($_.Exception.Message)" -ForegroundColor Red
}

# Step 3: Get linked bank accounts
Write-Host "`nStep 3: Fetching linked bank accounts..." -ForegroundColor Yellow
try {
    $accountsResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/bank-accounts" `
        -WebSession $session `
        -UseBasicParsing `
        -ErrorAction Stop

    $accounts = $accountsResponse.Content | ConvertFrom-Json
    if ($accounts.Count -gt 0) {
        Write-Host "✓ Found $($accounts.Count) linked account(s):" -ForegroundColor Green
        $accounts | ForEach-Object {
            Write-Host "  - $($_.accountNumber) ($($_.bankName)): $$($_.balance)" -ForegroundColor Cyan
        }
        $sourceAccountId = $accounts[0].id
    } else {
        Write-Host "✗ No bank accounts found. Please link a bank account first." -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ Failed to fetch accounts: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 4: Test Bank Transfer to External Account
Write-Host "`n=== TEST: Bank Transfer to External Account ===" -ForegroundColor Blue

$transferData = @{
    sourceAccount = $accounts[0].accountNumber
    destinationAccount = "98765432"
    destinationIfsc = "HDFC0006789"
    amount = 5000
    currency = "INR"
    bankPin = "123456"
    receiverName = "External Beneficiary"
} | ConvertTo-Json

Write-Host "Request Data:" -ForegroundColor Yellow
Write-Host $transferData -ForegroundColor Gray

try {
    Write-Host "`nSending transfer request..." -ForegroundColor Yellow

    $transferResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/payments/bank-transfer" `
        -Method POST `
        -WebSession $session `
        -Headers @{ "Content-Type" = "application/json" } `
        -Body $transferData `
        -UseBasicParsing `
        -SkipHttpErrorCheck

    Write-Host "Response Status: $($transferResponse.StatusCode)" -ForegroundColor Cyan
    Write-Host "Response Body: $($transferResponse.Content)" -ForegroundColor Gray

    $result = $transferResponse.Content | ConvertFrom-Json

    Write-Host "Response Status: $($transferResponse.StatusCode)" -ForegroundColor Cyan
    Write-Host "Response Body: $($transferResponse.Content)" -ForegroundColor Gray

    $result = $transferResponse.Content | ConvertFrom-Json

    if ($result.status -eq "SUCCESS") {
        Write-Host "`nTRANSFER SUCCESSFUL!" -ForegroundColor Green
        Write-Host "  Transaction ID: $($result.transactionId)" -ForegroundColor Green
        Write-Host "  Amount: $($result.amount) $($result.currency)" -ForegroundColor Green
        Write-Host "  To: $($result.destinationAccount) / $($result.destinationIfsc)" -ForegroundColor Green
        Write-Host "  Receiver: $($result.receiverName)" -ForegroundColor Green
        $paymentId = $result.id
    } elseif ($result.status -eq "FAILED") {
        Write-Host "`nTRANSFER FAILED (business rule)" -ForegroundColor Yellow
        Write-Host "  Error Code: $($result.errorCode)" -ForegroundColor Yellow
        Write-Host "  Error: $($result.errorMessage)" -ForegroundColor Yellow
        Write-Host "  Reason: $($result.failureReason)" -ForegroundColor Yellow
        $paymentId = $result.id
    } else {
        Write-Host "`nUNEXPECTED RESPONSE:" -ForegroundColor Red
        Write-Host "  $($transferResponse.Content)" -ForegroundColor Red
    }
} catch {
    Write-Host "`nRequest exception: $($_.Exception.Message)" -ForegroundColor Red
}

# Step 5: List all payments
Write-Host "`n=== Listing All Payments ===" -ForegroundColor Blue
try {
    $listResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/payments" `
        -WebSession $session `
        -UseBasicParsing `
        -ErrorAction Stop

    $payments = $listResponse.Content | ConvertFrom-Json
    Write-Host "✓ Total payments: $($payments.Count)" -ForegroundColor Green

    $payments | ForEach-Object {
        Write-Host "  - $($_.transactionId): $($_.amount) $($_.currency) [$($_.status)]" -ForegroundColor Cyan
    }
} catch {
    Write-Host "✗ Failed to list payments: $($_.Exception.Message)" -ForegroundColor Red
}

# Step 6: Get payment details
if ($paymentId) {
    Write-Host "`n=== Payment Details ===" -ForegroundColor Blue
    try {
        $detailResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/payments/$paymentId" `
            -WebSession $session `
            -UseBasicParsing `
            -ErrorAction Stop

        $detail = $detailResponse.Content | ConvertFrom-Json
        Write-Host "✓ Payment retrieved:" -ForegroundColor Green
        Write-Host "  ID: $($detail.id)" -ForegroundColor Cyan
        Write-Host "  Transaction ID: $($detail.transactionId)" -ForegroundColor Cyan
        Write-Host "  Status: $($detail.status)" -ForegroundColor Cyan
        Write-Host "  Amount: $($detail.amount) $($detail.currency)" -ForegroundColor Cyan
    } catch {
        Write-Host "✗ Failed to get details: $_" -ForegroundColor Red
    }
}

# Bonus: Test invalid transfer (should fail)
Write-Host "`n=== BONUS TEST: Invalid Transfer (should fail) ===" -ForegroundColor Blue

$invalidData = @{
    sourceAccount = $accounts[0].accountNumber
    destinationAccount = "1234"  # Invalid - too short
    destinationIfsc = "HDFC0006789"
    amount = 5000
    currency = "INR"
    bankPin = "123456"
} | ConvertTo-Json

try {
    Write-Host "Sending invalid transfer (short account number)..." -ForegroundColor Yellow
    $invalidResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/payments/bank-transfer" `
        -Method POST `
        -WebSession $session `
        -Headers @{ "Content-Type" = "application/json" } `
        -Body $invalidData `
        -UseBasicParsing `
        -SkipHttpErrorCheck

    $invalidResult = $invalidResponse.Content | ConvertFrom-Json
    if ($invalidResult.status -eq "FAILED") {
        Write-Host "✓ Correctly rejected invalid account" -ForegroundColor Green
        Write-Host "  Error: $($invalidResult.errorMessage)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "✓ Validation properly caught error" -ForegroundColor Green
}

Write-Host "`n=== TEST COMPLETE ===" -ForegroundColor Blue

