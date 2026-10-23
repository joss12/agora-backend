ALTER TABLE payment_attempt
    ADD COLUMN provider VARCHAR(20) NULL,
    ADD COLUMN provider_status VARCHAR(30) NULL;