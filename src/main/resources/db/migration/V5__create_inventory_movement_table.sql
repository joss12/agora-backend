CREATE TABLE inventory_movement (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                    product_id BIGINT NOT NULL,

                                    type VARCHAR(20) NOT NULL,

                                    quantity INT NOT NULL,

                                    stock_before INT NOT NULL,

                                    stock_after INT NOT NULL,

                                    created_at DATETIME NOT NULL,

                                    CONSTRAINT fk_inventory_movement_product
                                        FOREIGN KEY (product_id)
                                            REFERENCES product(id)
);