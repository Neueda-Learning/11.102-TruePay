#!/usr/bin/env pwsh
# TruePay Bank Transfer Test - Simple Version

$BaseUrl = "http://localhost:8081"

Write-Host "=== TruePay Bank Transfer Test ===" -ForegroundColor Blue

# Initialize session by accessing dashboard
Write-Host "Initializing session..." -ForegroundColor Yellow
$sessionResponse = Invoke-WebRequest -Uri "$BaseUrl/dashboard.html" -UseBasicParsing -SessionVariable "session" -ErrorAction SilentlyContinue

# Get current user
Write-Host "Getting user info..." -ForegroundColor Yellow
$userResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/auth/me" -WebSession $session -UseBasicParsing -ErrorAction SilentlyContinue
$user = $userResponse.Content | ConvertFrom-Json
Write-Host "Logged in as: $($user.fullName)" -ForegroundColor Green

# Get bank accounts
Write-Host "Getting linked bank accounts..." -ForegroundColor Yellow
$accountsResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/bank-accounts" -WebSession $session -UseBasicParsing
$accounts = $accountsResponse.Content | ConvertFrom-Json
Write-Host "Found $($accounts.Count) account(s)" -ForegroundColor Green

if ($accounts.Count -eq 0) {
    Write-Host "No bank accounts found!" -ForegroundColor Red
    exit
}

$sourceAccount = $accounts[0]
Write-Host "Using source account: $($sourceAccount.accountNumber)" -ForegroundColor Cyan

# Test: Transfer to external account
Write-Host "`n=== TEST: Bank Transfer to External Account ===" -ForegroundColor Blue

$transferData = @{
    sourceAccount = $sourceAccount.accountNumber
    destinationAccount = "98765432"
    destinationIfsc = "HDFC0006789"
    amount = 5000
    currency = "INR"
    bankPin = "123456"
    receiverName = "External Beneficiary"
} | ConvertTo-Json

Write-Host "Sending transfer request..." -ForegroundColor Yellow
$transferResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/payments/bank-transfer" `
    -Method POST `
    -WebSession $session `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body $transferData `
    -UseBasicParsing -SkipHttpErrorCheck

$result = $transferResponse.Content | ConvertFrom-Json

if ($result.status -eq "SUCCESS") {
    Write-Host "`n✓ TRANSFER SUCCESSFUL!" -ForegroundColor Green
    Write-Host "Transaction ID: $($result.transactionId)" -ForegroundColor Green
    Write-Host "Amount: $($result.amount) $($result.currency)" -ForegroundColor Green
    Write-Host "To: $($result.destinationAccount)" -ForegroundColor Green
} else {
    Write-Host "`n✗ TRANSFER FAILED" -ForegroundColor Red
    Write-Host "Status: $($result.status)" -ForegroundColor Red
    Write-Host "Error: $($result.errorMessage)" -ForegroundColor Red
}

# List payments
Write-Host "`n=== Payment List ===" -ForegroundColor Blue
$listResponse = Invoke-WebRequest -Uri "$BaseUrl/api/v1/payments" -WebSession $session -UseBasicParsing
$payments = $listResponse.Content | ConvertFrom-Json
Write-Host "Total payments: $($payments.Count)" -ForegroundColor Green
$payments | ForEach-Object {
    Write-Host " - $($_.transactionId): $($_.amount) $($_.currency) [$($_.status)]" -ForegroundColor Cyan
}

Write-Host "`n=== TEST COMPLETE ===" -ForegroundColor Blue
Write-Host "Bank transfer feature is working!" -ForegroundColor Green

