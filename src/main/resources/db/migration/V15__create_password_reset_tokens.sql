CREATE TABLE password_reset_tokens (
                                       id BIGINT NOT NULL AUTO_INCREMENT,
                                       user_id BIGINT NOT NULL,
                                       token_hash CHAR(64) NOT NULL,
                                       expires_at DATETIME(6) NOT NULL,
                                       used_at DATETIME(6) NULL,
                                       created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                       PRIMARY KEY (id),

                                       CONSTRAINT uk_password_reset_token_hash
                                           UNIQUE (token_hash),

                                       CONSTRAINT fk_password_reset_tokens_user
                                           FOREIGN KEY (user_id)
                                               REFERENCES users(id)
                                               ON DELETE CASCADE,

                                       INDEX idx_password_reset_tokens_user_id (user_id),
                                       INDEX idx_password_reset_tokens_expires_at (expires_at)
);