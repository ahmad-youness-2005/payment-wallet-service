ALTER TABLE transfers
    DROP INDEX uk_sender_idempotency;

ALTER TABLE transfers
    DROP COLUMN idempotency_key;

