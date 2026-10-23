
ALTER TABLE orders
    ADD COLUMN recipient_name VARCHAR(100) NULL,
    ADD COLUMN phone_number VARCHAR(20) NULL,
    ADD COLUMN postal_code VARCHAR(5) NULL,
    ADD COLUMN delivery_address VARCHAR(255) NULL,
    ADD COLUMN address_detail VARCHAR(255) NULL,
    ADD COLUMN delivery_instructions VARCHAR(500) NULL;
