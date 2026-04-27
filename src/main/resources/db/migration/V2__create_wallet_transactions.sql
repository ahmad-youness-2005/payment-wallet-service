CREATE TABLE wallet_transactions (
    id CHAR(36) PRIMARY KEY,
    wallet_id CHAR(36) NOT NULL,
    transfer_id CHAR(36) NULL,
    operation_type VARCHAR(40) NOT NULL,
    operation_status VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    counterparty_user_id CHAR(36) NULL,
    reference VARCHAR(80) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_tx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_wallet_tx_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id)
);

CREATE INDEX idx_wallet_tx_wallet_created_at ON wallet_transactions(wallet_id, created_at DESC);
