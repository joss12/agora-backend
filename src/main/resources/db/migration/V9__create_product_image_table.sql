
CREATE TABLE product_image (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               product_id BIGINT NOT NULL,
                               public_id VARCHAR(255) NOT NULL,
                               image_url VARCHAR(2048) NOT NULL,
                               is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                               display_order INT NOT NULL DEFAULT 0,
                               created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                               PRIMARY KEY (id),

                               CONSTRAINT fk_product_image_product
                                   FOREIGN KEY (product_id)
                                       REFERENCES product(id)
                                       ON DELETE CASCADE,

                               CONSTRAINT uk_product_image_public_id
                                   UNIQUE (public_id),

                               INDEX idx_product_image_product (product_id)
);
