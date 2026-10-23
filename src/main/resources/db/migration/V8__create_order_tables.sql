CREATE TABLE orders (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        user_id BIGINT NOT NULL,

                        status VARCHAR(30) NOT NULL,

                        total_amount DECIMAL(12, 2) NOT NULL,

                        created_at DATETIME NOT NULL,
                        updated_at DATETIME NOT NULL,

                        CONSTRAINT fk_orders_user
                            FOREIGN KEY (user_id)
                                REFERENCES users(id)
);

CREATE TABLE order_item (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,

                            order_id BIGINT NOT NULL,
                            product_id BIGINT NOT NULL,

                            product_name VARCHAR(200) NOT NULL,
                            sku VARCHAR(100),

                            unit_price DECIMAL(12, 2) NOT NULL,
                            quantity INT NOT NULL,
                            subtotal DECIMAL(12, 2) NOT NULL,

                            CONSTRAINT fk_order_item_order
                                FOREIGN KEY (order_id)
                                    REFERENCES orders(id)
                                    ON DELETE CASCADE,

                            CONSTRAINT fk_order_item_product
                                FOREIGN KEY (product_id)
                                    REFERENCES product(id)
);