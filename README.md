# TruePay

Tagline: **Where every payment meets trust**

TruePay is a Spring Boot payments platform with a REST API and a lightweight dashboard UI.

## What is implemented

- Direct dashboard access without login, registration, or logout screens
- Automatic single-user session bootstrap for local/demo usage
- Profile card with personal tab and bank details tab
- Unlimited linked bank accounts with combined balance
- Delete bank account (allowed only when account balance is zero)
- Pay to UPI card
- Bank-to-bank transfer card with sender account selection
- Beneficiary management (add/list/delete) for repeat transfers
- Payment lifecycle and audit history:
  - `PENDING -> SUCCESS`
  - `FAILED` and `CANCELLED`
- Fraud checks for high-value and high-frequency transactions
- Dashboard graphs (status distribution, fraud/failure, payment volume)
- OpenAPI docs at `/swagger-ui`

## Bank Transfer Flow (Implemented)

- Sender selects linked source account
- Receiver details come from:
  - selected beneficiary, or
  - manual input (`receiverName`, `destinationAccount`, `destinationIfsc`)
- Sender enters amount, optional reference, app PIN (4 digits), bank PIN (6 digits)
- Sender enters amount, optional reference, and bank PIN (6 digits)
- System validates and performs fraud checks
- If receiver account exists in TruePay, receiver balance is credited
- If receiver account is external, transfer is simulated and completed

## Tech Stack

- Java 21+
- Spring Boot 3
- Spring Web, Spring Data JPA, Validation
- MySQL runtime DB
- H2 for tests
- Static HTML/CSS/JS dashboard
- Chart.js for graphs

## Run locally

1. Create MySQL database `truepay`.
2. Set DB environment variables.
3. Run the app.

```powershell
Set-Location "C:\Users\Administrator\Desktop\TruePay\untitled\11.102-TruePay"
$env:DB_URL="jdbc:mysql://localhost:3306/truepay?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USER="root"
$env:DB_PASSWORD="<your-password>"
mvn spring-boot:run
```

Then open:

- `http://localhost:8080/`
- `http://localhost:8080/dashboard.html`

## API Overview

### Current User

- `GET /api/v1/auth/me`

### Bank Accounts

- `POST /api/v1/bank-accounts`
- `GET /api/v1/bank-accounts`
- `DELETE /api/v1/bank-accounts/{accountId}`
- `GET /api/v1/bank-accounts/combined-balance`

### Beneficiaries

- `POST /api/v1/beneficiaries`
- `GET /api/v1/beneficiaries`
- `DELETE /api/v1/beneficiaries/{beneficiaryId}`

### Payments

- `POST /api/v1/payments/pay-to-upi`
- `POST /api/v1/payments/pay-to-bank`
- `POST /api/v1/payments/upi`
- `POST /api/v1/payments/bank-transfer`
- `POST /api/v1/payments/{paymentId}/cancel`
- `GET /api/v1/payments/verify-receiver?accountNumber=...&ifscCode=...`
- `GET /api/v1/payments`
- `GET /api/v1/payments/{paymentId}`
- `GET /api/v1/payments/{paymentId}/history`
- `GET /api/v1/payments/history/{userId}`

### Audit

- `GET /api/v1/payments/audits`
- `GET /api/v1/audit/history/{userId}`

### Dashboard Summary

- `GET /api/v1/dashboard/summary`

## Jenkins (Linux Agent) Starter

`Jenkinsfile` is included in the project root.

## GDPR + DPDP Baseline Implemented

- Minimized stored user/payment fields
- Sensitive values hashed at rest (`appPin`, `bankPin`, internal password hash`)
- Credentials loaded from environment variables (not hardcoded)
- Clear structured error codes for API consumers

## Notes

- The removed `login.html` and `register.html` pages now redirect to `dashboard.html` for backward compatibility.
- Local/demo sessions automatically use a default profile seeded on first access.
- Demo bank PIN is required for transaction flows

## Next Enhancements

- OTP/device verification for high-risk transfers
- Data export/delete endpoints and retention jobs
- Enhanced anomaly detection and alert review workflow

