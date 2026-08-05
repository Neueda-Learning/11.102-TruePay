# Bank-to-Bank Transfer Implementation Guide

## Overview
TruePay now supports bank-to-bank transfers to **any destination account**, whether it exists in the system or not. You can transfer money from your linked bank accounts to:
- External bank accounts (random account numbers)
- Internal accounts (other users' accounts in the system)

## Features Implemented

### 1. **Transfer to Any External Account**
- Transfer to any valid bank account with a valid IFSC code
- No need for the destination account to exist in the system
- Money is deducted from your source account
- Direct transfers are simulated and marked as SUCCESS

### 2. **Transfer to Internal Accounts**
- Transfer to other users' accounts already linked in the system
- Automatic balance credit to destination account
- Receiver name auto-populated from system

### 3. **Validation & Security**
- Fund availability check (insufficient funds protection)
- Fraud detection for high-value and frequent transactions
- Bank PIN validation (6-digit PIN required)
- Account number validation (8-20 digits)
- IFSC code validation (IFSC standard format: 4 letters + 0 + 6 alphanumerics)
- Sender and receiver must be different accounts

---

## API Endpoint

### POST `/api/v1/payments/bank-transfer`

#### Request Body
```json
{
  "sourceAccount": "ACC_1 OR 12345678",
  "destinationAccount": "98765432",
  "destinationIfsc": "HDFC0006789",
  "amount": 5000.00,
  "currency": "INR",
  "bankPin": "123456",
  "receiverName": "John Doe"
}
```

#### Field Descriptions

| Field | Type | Required | Description | Format |
|-------|------|----------|-------------|--------|
| `sourceAccount` | String | ✓ | Your linked bank account identifier | Account number or alias (e.g., "ACC_1") |
| `destinationAccount` | String | ✓ | Recipient's account number | 8-20 digits |
| `destinationIfsc` | String | ✓ | Recipient's bank IFSC code | 4 letters + 0 + 6 alphanumerics (e.g., HDFC0006789) |
| `amount` | Decimal | ✓ | Transfer amount | > 0 and ≤ 1,000,000 |
| `currency` | String | ✓ | Currency code | ISO-4217 (INR, USD, EUR, GBP) |
| `bankPin` | String | ✓ | Your 6-digit bank PIN | Exactly 6 digits |
| `receiverName` | String | ✗ | Recipient's name (optional, auto-populated if in system) | Free text |

#### Response
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "transactionId": "TXN1722884400000abcd1234",
  "userId": 1,
  "sourceAccountId": 1,
  "method": "BANK_TRANSFER",
  "amount": 5000.00,
  "currency": "INR",
  "status": "SUCCESS",
  "message": "Payment completed successfully",
  "failureReason": null,
  "errorCode": null,
  "errorMessage": null,
  "destinationUpiId": null,
  "destinationAccount": "98765432",
  "destinationIfsc": "HDFC0006789",
  "receiverName": "John Doe",
  "referenceRemark": null,
  "createdAt": "2026-08-05T20:25:00Z",
  "updatedAt": "2026-08-05T20:25:00Z"
}
```

---

## Usage Examples

### Example 1: Transfer to External Account
```bash
curl -X POST http://localhost:8081/api/v1/payments/bank-transfer \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccount": "ACC_1",
    "destinationAccount": "98765432",
    "destinationIfsc": "HDFC0006789",
    "amount": 10000.00,
    "currency": "INR",
    "bankPin": "123456",
    "receiverName": "Rajesh Kumar"
  }'
```

### Example 2: PowerShell Script

```powershell
$transferData = @{
    sourceAccount = "ACC_1"
    destinationAccount = "98765432"
    destinationIfsc = "HDFC0006789"
    amount = 5000.00
    currency = "INR"
    bankPin = "123456"
    receiverName = "External Bank Receiver"
} | ConvertTo-Json

$response = Invoke-WebRequest -Uri "http://localhost:8081/api/v1/payments/bank-transfer" `
    -Method POST `
    -ContentType "application/json" `
    -Body $transferData `
    -UseBasicParsing

$response.Content | ConvertFrom-Json | Format-Object
```

### Example 3: Using REST Client (VS Code)

Create a file named `transfer-request.http`:
```http
### Bank Transfer to External Account
POST http://localhost:8081/api/v1/payments/bank-transfer
Content-Type: application/json

{
  "sourceAccount": "ACC_1",
  "destinationAccount": "98765432",
  "destinationIfsc": "HDFC0006789",
  "amount": 15000.00,
  "currency": "INR",
  "bankPin": "123456",
  "receiverName": "Unknown Beneficiary"
}
```

---

## Error Scenarios & Solutions

### Error: Invalid Account Number
```json
{
  "status": "FAILED",
  "errorCode": "INVALID_ACCOUNT",
  "errorMessage": "Receiver account number is invalid"
}
```
✅ **Solution**: Account number must be 8-20 digits. Example: `98765432`

### Error: Invalid IFSC Code
```json
{
  "status": "FAILED",
  "errorCode": "INVALID_ACCOUNT",
  "errorMessage": "Receiver IFSC format is invalid"
}
```
✅ **Solution**: IFSC must follow format: 4 letters + 0 + 6 alphanumerics
- ✅ Valid: `HDFC0006789`
- ✅ Valid: `ICIC0000123`
- ❌ Invalid: `HDFC123456` (missing 0 in 5th position)

### Error: Insufficient Funds
```json
{
  "status": "FAILED",
  "errorCode": "INSUFFICIENT_FUNDS",
  "errorMessage": "Insufficient balance"
}
```
✅ **Solution**: Ensure your source account has sufficient balance

### Error: Invalid Bank PIN
```json
{
  "status": "FAILED",
  "errorCode": "PIN_VALIDATION_FAILED",
  "errorMessage": "Invalid bank PIN"
}
```
✅ **Solution**: Bank PIN must be exactly 6 digits

### Error: Suspicious Transaction (Fraud Alert)
```json
{
  "status": "FAILED",
  "errorCode": "SUSPICIOUS_TRANSACTION",
  "errorMessage": "High-value transaction detected"
}
```
✅ **Solution**:
- Transactions > ₹50,000 trigger fraud alerts
- Multiple transactions (>3) in a minute trigger alerts
- Wait a minute between transfers or reduce amount

---

## Step-by-Step Process

### 1. **Prepare Your Transfer**
   - Select source account from your linked accounts
   - Verify destination account number and IFSC
   - Confirm transfer amount
   - Have your 6-digit bank PIN ready

### 2. **Validate Destination Account**
   - Use `GET /api/v1/payments/verify-receiver?accountNumber=98765432&ifscCode=HDFC0006789`
   - This verifies if the account exists in the system

### 3. **Submit Transfer**
   - Call `POST /api/v1/payments/bank-transfer` with all required fields
   - System validates all fields and checks fund availability
   - Fraud checks are performed

### 4. **Monitor Status**
   - Check response status (SUCCESS, FAILED, PENDING)
   - Use `GET /api/v1/payments/{paymentId}` to check payment details
   - Query `GET /api/v1/payments?status=SUCCESS` to view completed transfers

### 5. **View History**
   - Call `GET /api/v1/payments` to list all payments
   - Call `GET /api/v1/payments/{paymentId}/history` to view status changes

---

## Technical Details

### Payment Method Handling
- **BANK_TRANSFER**: Used for bank-to-bank transfers
- Supports both internal and external accounts
- Amount is deducted from source immediately
- Amount is credited to destination (if internal account exists)

### Transaction Lifecycle
1. **PENDING**: Payment created, awaiting validation
2. **SUCCESS**: Payment completed (balance transferred)
3. **FAILED**: Payment failed (validation error, fraud, insufficient funds)
4. **CANCELLED**: Payment cancelled by user (only from PENDING state)

### Audit & Compliance
- All transfers are logged in `audit_logs` table
- Payment history is maintained in `payment_status_history`
- Fraud alerts are recorded in `fraud_alerts` table
- Transaction IDs are unique and trackable

---

## Browser Access

1. **Dashboard**: http://localhost:8081/dashboard.html
2. **API Documentation**: http://localhost:8081/swagger-ui
3. **API Schema**: http://localhost:8081/api-docs

---

## Database Schema

### Key Tables
- `payments`: Main payment records
- `bank_accounts`: Linked user bank accounts
- `payment_status_history`: Status change audit trail
- `fraud_alerts`: Suspicious transaction records
- `audit_logs`: Comprehensive action logs

---

## Security Notes
⚠️ **Important**:
- Bank PINs are hashed and never stored in plaintext
- All transfers require valid bank PIN
- Failed PINs trigger audit logs
- Fraud detection runs automatically
- All requests must be over HTTPS in production
- Session-based authentication required

---

## Troubleshooting

### Transfer showing as PENDING
- Check `GET /api/v1/payments/{paymentId}/history` for detailed status
- If stuck for > 5 minutes, try cancelling with `POST /api/v1/payments/{paymentId}/cancel`

### Destination account not found but transfer succeeds
- This is expected behavior! External accounts don't need to exist in system
- Only internal transfers (accounts in system) auto-credit destination

### High fraud alerts
- Reduce transfer amount or wait between transfers
- Contact admin if limits are too restrictive

---

## Limits & Constraints
- **Max single transfer**: ₹1,000,000
- **Fraud threshold amount**: ₹50,000
- **Transaction frequency limit**: > 3 transactions in 1 minute = suspicious
- **Account number length**: 8-20 digits
- **Supported currencies**: INR, USD, EUR, GBP

---

## Support
For issues or questions, please refer to the main README.md or contact support.

