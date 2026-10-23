
CREATE TABLE payment (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         order_id BIGINT NOT NULL UNIQUE,
                         amount DECIMAL(12, 2) NOT NULL,
                         status VARCHAR(20) NOT NULL,
                         transaction_reference VARCHAR(100),
                         created_at DATETIME NOT NULL,
                         updated_at DATETIME NOT NULL,

                         CONSTRAINT fk_payment_order
                             FOREIGN KEY (order_id)
                                 REFERENCES orders(id)
);

CREATE TABLE payment_attempt (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 payment_id BIGINT NOT NULL,
                                 outcome VARCHAR(20) NOT NULL,
                                 transaction_reference VARCHAR(100) NOT NULL UNIQUE,
                                 created_at DATETIME NOT NULL,

                                 CONSTRAINT fk_payment_attempt_payment
                                     FOREIGN KEY (payment_id)
                                         REFERENCES payment(id)
);

CREATE INDEX idx_payment_attempt_payment
    ON payment_attempt(payment_id);
