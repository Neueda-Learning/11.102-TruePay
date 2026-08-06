CREATE DATABASE IF NOT EXISTS truepay;
USE truepay;

CREATE TABLE IF NOT EXISTS user_profiles (
	id BIGINT NOT NULL AUTO_INCREMENT,
	full_name VARCHAR(255) NOT NULL,
	email VARCHAR(255) NOT NULL,
	mobile VARCHAR(255) NOT NULL,
	app_pin_hash VARCHAR(255) NOT NULL,
	password_hash VARCHAR(255) NOT NULL,
	PRIMARY KEY (id),
	UNIQUE KEY uk_user_profiles_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bank_accounts (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	account_holder_name VARCHAR(255) NOT NULL,
	bank_name VARCHAR(255) NOT NULL,
	account_number VARCHAR(255) NOT NULL,
	ifsc_code VARCHAR(255) NOT NULL,
	bank_pin_hash VARCHAR(255) NOT NULL,
	account_type VARCHAR(255) NOT NULL,
	balance DECIMAL(19,2) NOT NULL,
	PRIMARY KEY (id),
	UNIQUE KEY uk_bank_accounts_account_number (account_number),
	KEY idx_bank_accounts_user_id (user_id),
	CONSTRAINT fk_bank_accounts_user FOREIGN KEY (user_id) REFERENCES user_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS beneficiaries (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	name VARCHAR(255) NOT NULL,
	account_number VARCHAR(255) NOT NULL,
	ifsc_code VARCHAR(255) NOT NULL,
	PRIMARY KEY (id),
	KEY idx_beneficiaries_user_id (user_id),
	CONSTRAINT fk_beneficiaries_user FOREIGN KEY (user_id) REFERENCES user_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_limits (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	daily_enabled BIT(1) NOT NULL,
	daily_limit DECIMAL(19,2) DEFAULT NULL,
	monthly_enabled BIT(1) NOT NULL,
	monthly_limit DECIMAL(19,2) DEFAULT NULL,
	per_transaction_enabled BIT(1) NOT NULL,
	per_transaction_limit DECIMAL(19,2) DEFAULT NULL,
	PRIMARY KEY (id),
	UNIQUE KEY uk_payment_limits_user_id (user_id),
	CONSTRAINT fk_payment_limits_user FOREIGN KEY (user_id) REFERENCES user_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payments (
	id BINARY(16) NOT NULL,
	transaction_id VARCHAR(32) NOT NULL,
	idempotency_key VARCHAR(255) NOT NULL,
	user_id BIGINT NOT NULL,
	source_account_id BIGINT DEFAULT NULL,
	method VARCHAR(255) NOT NULL,
	amount DECIMAL(19,2) NOT NULL,
	currency VARCHAR(3) NOT NULL,
	destination_upi_id VARCHAR(255) DEFAULT NULL,
	destination_account VARCHAR(255) DEFAULT NULL,
	destination_ifsc VARCHAR(255) DEFAULT NULL,
	receiver_name VARCHAR(255) DEFAULT NULL,
	reference_remark VARCHAR(255) DEFAULT NULL,
	receiver_type VARCHAR(255) NOT NULL,
	status VARCHAR(255) NOT NULL,
	error_code VARCHAR(255) DEFAULT NULL,
	error_message VARCHAR(255) DEFAULT NULL,
	failure_reason VARCHAR(255) DEFAULT NULL,
	created_at DATETIME(6) NOT NULL,
	updated_at DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	UNIQUE KEY uk_payments_transaction_id (transaction_id),
	KEY idx_payments_user_id (user_id),
	KEY idx_payments_source_account_id (source_account_id),
	CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES user_profiles (id),
	CONSTRAINT fk_payments_source_account FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_status_history (
	id BIGINT NOT NULL AUTO_INCREMENT,
	payment_id BINARY(16) NOT NULL,
	status VARCHAR(255) NOT NULL,
	triggered_by VARCHAR(255) NOT NULL,
	changed_at DATETIME(6) NOT NULL,
	notes VARCHAR(255) DEFAULT NULL,
	PRIMARY KEY (id),
	KEY idx_payment_status_history_payment_id (payment_id),
	CONSTRAINT fk_payment_status_history_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fraud_alerts (
	id BIGINT NOT NULL AUTO_INCREMENT,
	payment_id BINARY(16) NOT NULL,
	reason VARCHAR(255) NOT NULL,
	risk_score INT NOT NULL,
	created_at DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	KEY idx_fraud_alerts_payment_id (payment_id),
	CONSTRAINT fk_fraud_alerts_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	transaction_id VARCHAR(32) NOT NULL,
	action VARCHAR(64) NOT NULL,
	description VARCHAR(512) NOT NULL,
	`timestamp` DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	KEY idx_audit_logs_user_id (user_id),
	CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES user_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO user_profiles (id, full_name, email, mobile, app_pin_hash, password_hash)
VALUES
	(1, 'TruePay Demo User', 'demo@truepay.local', '9999999999',
	 '$2b$10$8XDAz41C1zgODY1/Vr6HEeTqKZKa6X.yVRyhT8tePXFOnWcX0nwA2',
	 '$2b$10$X.Wnv1Zvtkfp6QZiEBMObOawCrCQuTxaODWW0144xoExYeO2fH2fe')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO bank_accounts (id, user_id, account_holder_name, bank_name, account_number, ifsc_code, bank_pin_hash, account_type, balance)
VALUES
	(1, 1, 'TruePay Demo User', 'TruePay Bank', '500001234567', 'HDFC0123456', '$2b$10$68EMlldEudA3gqLgoONV9.yd3v8iNl1VPHcKkHyIqdvimkao/FAxK', 'SAVINGS', 75000.00),
	(2, 1, 'TruePay Demo User', 'TruePay Bank', '500009876543', 'ICIC0123456', '$2b$10$68EMlldEudA3gqLgoONV9.yd3v8iNl1VPHcKkHyIqdvimkao/FAxK', 'CURRENT', 12000.00)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO beneficiaries (id, user_id, name, account_number, ifsc_code)
VALUES
	(1, 1, 'Alex Receiver', '900001112223', 'SBIN0123456')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO payment_limits (id, user_id, daily_enabled, daily_limit, monthly_enabled, monthly_limit, per_transaction_enabled, per_transaction_limit)
VALUES
	(1, 1, b'0', NULL, b'0', NULL, b'0', NULL)
ON DUPLICATE KEY UPDATE id = id;

SET @payment_success_id = UUID_TO_BIN('11111111-1111-1111-1111-111111111111');
SET @payment_failed_id = UUID_TO_BIN('22222222-2222-2222-2222-222222222222');

INSERT INTO payments (
	id, transaction_id, idempotency_key, user_id, source_account_id, method, amount, currency,
	destination_upi_id, destination_account, destination_ifsc, receiver_name, reference_remark,
	receiver_type, status, error_code, error_message, failure_reason, created_at, updated_at
)
VALUES
	(
		@payment_success_id,
		'TXNDEMO000000000000000000000001',
		'demo-idem-0001',
		1,
		1,
		'BANK_TRANSFER',
		1500.00,
		'INR',
		NULL,
		'900001112223',
		'SBIN0123456',
		'Alex Receiver',
		'Demo transfer to beneficiary',
		'BANK_ACCOUNT',
		'SUCCESS',
		NULL,
		NULL,
		NULL,
		'2026-08-01 10:00:00.000000',
		'2026-08-01 10:00:02.000000'
	),
	(
		@payment_failed_id,
		'TXNDEMO000000000000000000000002',
		'demo-idem-0002',
		1,
		2,
		'UPI',
		25000.00,
		'INR',
		'merchant@upi',
		NULL,
		NULL,
		'merchant@upi',
		'Demo failed UPI payment',
		'UPI_ID',
		'FAILED',
		'INSUFFICIENT_FUNDS',
		'Insufficient balance',
		'Insufficient balance',
		'2026-08-02 09:30:00.000000',
		'2026-08-02 09:30:01.000000'
	)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO payment_status_history (id, payment_id, status, triggered_by, changed_at, notes)
VALUES
	(1, @payment_success_id, 'PENDING', 'SYSTEM', '2026-08-01 10:00:00.000000', 'Payment initiated'),
	(2, @payment_success_id, 'SUCCESS', 'SYSTEM', '2026-08-01 10:00:02.000000', 'Payment completed successfully'),
	(3, @payment_failed_id, 'PENDING', 'SYSTEM', '2026-08-02 09:30:00.000000', 'Payment initiated'),
	(4, @payment_failed_id, 'FAILED', 'SYSTEM', '2026-08-02 09:30:01.000000', 'Insufficient balance')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO audit_logs (id, user_id, transaction_id, action, description, `timestamp`)
VALUES
	(1, 1, 'TXNDEMO000000000000000000000001', 'PAYMENT_CREATED', 'Demo payment created for beneficiary transfer', '2026-08-01 10:00:00.000000'),
	(2, 1, 'TXNDEMO000000000000000000000001', 'PAYMENT_SUCCESS', 'Payment of INR 1500.00 completed', '2026-08-01 10:00:02.000000'),
	(3, 1, 'TXNDEMO000000000000000000000002', 'PAYMENT_CREATED', 'Demo UPI payment initiated', '2026-08-02 09:30:00.000000'),
	(4, 1, 'TXNDEMO000000000000000000000002', 'PAYMENT_FAILED', 'Payment failed: Insufficient balance', '2026-08-02 09:30:01.000000')
ON DUPLICATE KEY UPDATE id = id;

