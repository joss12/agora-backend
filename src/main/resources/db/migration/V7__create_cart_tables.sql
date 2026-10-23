CREATE TABLE cart (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      user_id BIGINT NOT NULL UNIQUE,
                      created_at DATETIME NOT NULL,
                      updated_at DATETIME NOT NULL,

                      CONSTRAINT fk_cart_user
                          FOREIGN KEY (user_id)
                              REFERENCES users(id)
);

CREATE TABLE cart_item (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           cart_id BIGINT NOT NULL,
                           product_id BIGINT NOT NULL,
                           quantity INT NOT NULL,
                           created_at DATETIME NOT NULL,
                           updated_at DATETIME NOT NULL,

                           CONSTRAINT fk_cart_item_cart
                               FOREIGN KEY (cart_id)
                                   REFERENCES cart(id)
                                   ON DELETE CASCADE,

                           CONSTRAINT fk_cart_item_product
                               FOREIGN KEY (product_id)
                                   REFERENCES product(id),

                           CONSTRAINT uk_cart_item_product
                               UNIQUE (cart_id, product_id)
);