CREATE TABLE user_address (
                              id BIGINT NOT NULL AUTO_INCREMENT,
                              user_id BIGINT NOT NULL,
                              label VARCHAR(50) NOT NULL,
                              recipient_name VARCHAR(200) NOT NULL,
                              phone_number VARCHAR(30) NOT NULL,
                              postal_code VARCHAR(20) NOT NULL,
                              address VARCHAR(500) NOT NULL,
                              address_detail VARCHAR(500) NULL,
                              is_default BOOLEAN NOT NULL DEFAULT FALSE,
                              created_at DATETIME(6) NOT NULL,
                              updated_at DATETIME(6) NOT NULL,

                              PRIMARY KEY (id),

                              CONSTRAINT fk_user_address_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES users(id)
                                      ON DELETE CASCADE
);

CREATE INDEX idx_user_address_user_id
    ON user_address(user_id);

CREATE INDEX idx_user_address_user_default
    ON user_address(user_id, is_default);