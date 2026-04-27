CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wallets (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL UNIQUE,
    available_balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    daily_transferred_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    last_transfer_date DATE NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transfers (
    id CHAR(36) PRIMARY KEY,
    reference VARCHAR(64) NOT NULL UNIQUE,
    sender_wallet_id CHAR(36) NOT NULL,
    receiver_wallet_id CHAR(36) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(90) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfer_sender FOREIGN KEY (sender_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_transfer_receiver FOREIGN KEY (receiver_wallet_id) REFERENCES wallets(id),
    CONSTRAINT uk_sender_idempotency UNIQUE (sender_wallet_id, idempotency_key)
);

CREATE TABLE ledger_entries (
    id CHAR(36) PRIMARY KEY,
    transfer_id CHAR(36) NOT NULL,
    wallet_id CHAR(36) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id),
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

CREATE TABLE audit_events (
    id CHAR(36) PRIMARY KEY,
    event_type VARCHAR(60) NOT NULL,
    actor_user_id CHAR(36) NULL,
    details VARCHAR(300) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (actor_user_id) REFERENCES users(id)
);
