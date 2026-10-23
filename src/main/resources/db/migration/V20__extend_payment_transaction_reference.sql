ALTER TABLE payment
    MODIFY COLUMN transaction_reference VARCHAR(200) NULL;

ALTER TABLE payment
    ADD COLUMN toss_order_id VARCHAR(64) NULL;

ALTER TABLE payment
    ADD CONSTRAINT uk_payment_toss_order_id
        UNIQUE (toss_order_id);