CREATE TABLE product_reviews (
                                 id BIGINT NOT NULL AUTO_INCREMENT,

                                 user_id BIGINT NOT NULL,
                                 product_id BIGINT NOT NULL,

                                 rating INT NOT NULL,
                                 title VARCHAR(150),
                                 content TEXT NOT NULL,

                                 created_at DATETIME(6) NOT NULL,
                                 updated_at DATETIME(6) NOT NULL,

                                 PRIMARY KEY (id),

                                 CONSTRAINT uk_product_reviews_user_product
                                     UNIQUE (user_id, product_id),

                                 CONSTRAINT fk_product_reviews_user
                                     FOREIGN KEY (user_id)
                                         REFERENCES users(id),

                                 CONSTRAINT fk_product_reviews_product
                                     FOREIGN KEY (product_id)
                                         REFERENCES product(id),

                                 CONSTRAINT chk_product_reviews_rating
                                     CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_product_reviews_product_created
    ON product_reviews (product_id, created_at);