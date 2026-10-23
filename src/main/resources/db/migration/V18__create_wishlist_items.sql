CREATE TABLE wishlist_items (
                                id BIGINT NOT NULL AUTO_INCREMENT,
                                user_id BIGINT NOT NULL,
                                product_id BIGINT NOT NULL,
                                created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                PRIMARY KEY (id),

                                CONSTRAINT uk_wishlist_user_product
                                    UNIQUE (user_id, product_id),

                                CONSTRAINT fk_wishlist_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(id)
                                        ON DELETE CASCADE,

                                CONSTRAINT fk_wishlist_product
                                    FOREIGN KEY (product_id)
                                        REFERENCES product(id)
                                        ON DELETE CASCADE,

                                INDEX idx_wishlist_user_created (user_id, created_at)
);