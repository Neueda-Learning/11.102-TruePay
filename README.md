# TruePay

Tagline: **Where every payment meets trust**

TruePay is a Spring Boot payments platform with a REST API and a lightweight dashboard UI.

## What is implemented

- Session-based `register`, `login`, `logout`, and `me` APIs
- Dashboard pages: `login`, `register`, `dashboard`
- Profile card with personal tab and bank details tab
- Unlimited linked bank accounts with combined balance
- Delete bank account (allowed only when account balance is zero)
- Pay to UPI card
- Bank-to-bank transfer card with sender account selection
- Beneficiary management (add/list/delete) for repeat transfers
- Payment lifecycle and audit history:
  - `CREATED -> VALIDATED -> SENT -> COMPLETED`
  - `FAILED` can happen at any stage
- Fraud checks for high-value and high-frequency transactions
- Idempotency by `idempotencyKey` per user
- Dashboard graphs (status distribution, fraud/failure, payment volume)
- OpenAPI docs at `/swagger-ui`

## Bank Transfer Flow (Implemented)

- Sender selects linked source account
- Receiver details come from:
  - selected beneficiary, or
  - manual input (`receiverName`, `destinationAccount`, `destinationIfsc`)
- Sender enters amount, optional reference, app PIN (4 digits), bank PIN (6 digits)
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
Set-Location "C:\Users\Administrator\Desktop\Project\TruePay"
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="truepay"
$env:DB_USER="root"
$env:DB_PASSWORD="<your-password>"
mvn spring-boot:run
```

Then open:

- `http://localhost:8080/register.html`
- `http://localhost:8080/login.html`
- `http://localhost:8080/dashboard.html`

## API Overview

### Authentication

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
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
- `GET /api/v1/payments/verify-receiver?accountNumber=...&ifscCode=...`
- `GET /api/v1/payments`
- `GET /api/v1/payments/{paymentId}`
- `GET /api/v1/payments/{paymentId}/history`

### Dashboard Summary

- `GET /api/v1/dashboard/summary`

## Jenkins (Linux Agent) Starter

`Jenkinsfile` is included in the project root.

## GDPR + DPDP Baseline Implemented

- Minimized stored user/payment fields
- Sensitive values hashed at rest (`appPin`, `bankPin`, login `password`)
- Credentials loaded from environment variables (not hardcoded)
- Session-based auth for protected actions
- Clear structured error codes for API consumers

## Next Enhancements

- OTP/device verification for high-risk transfers
- Data export/delete endpoints and retention jobs
- Enhanced anomaly detection and alert review workflow

