CREATE TABLE coupons (
                         id BIGINT NOT NULL AUTO_INCREMENT,

                         code VARCHAR(50) NOT NULL,

                         discount_type VARCHAR(20) NOT NULL,

                         discount_value DECIMAL(12, 2) NOT NULL,

                         minimum_order_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,

                         maximum_discount_amount DECIMAL(12, 2) NULL,

                         usage_limit INT NULL,

                         usage_limit_per_user INT NULL,

                         usage_count INT NOT NULL DEFAULT 0,

                         starts_at DATETIME(6) NOT NULL,

                         expires_at DATETIME(6) NOT NULL,

                         active BOOLEAN NOT NULL DEFAULT TRUE,

                         deleted BOOLEAN NOT NULL DEFAULT FALSE,

                         created_at DATETIME(6) NOT NULL,

                         updated_at DATETIME(6) NOT NULL,

                         PRIMARY KEY (id),

                         CONSTRAINT uk_coupons_code UNIQUE (code),

                         CONSTRAINT chk_coupons_discount_value
                             CHECK (discount_value > 0),

                         CONSTRAINT chk_coupons_minimum_amount
                             CHECK (minimum_order_amount >= 0),

                         CONSTRAINT chk_coupons_usage_count
                             CHECK (usage_count >= 0),

                         CONSTRAINT chk_coupons_usage_limit
                             CHECK (usage_limit IS NULL OR usage_limit > 0),

                         CONSTRAINT chk_coupons_user_limit
                             CHECK (
                                 usage_limit_per_user IS NULL
                                     OR usage_limit_per_user > 0
                                 ),

                         CONSTRAINT chk_coupons_dates
                             CHECK (expires_at > starts_at),

                         CONSTRAINT chk_coupons_discount_type
                             CHECK (discount_type IN ('PERCENTAGE', 'FIXED')),

                         CONSTRAINT chk_coupons_percentage
                             CHECK (
                                 discount_type <> 'PERCENTAGE'
                                     OR discount_value <= 100
                                 ),

                         CONSTRAINT chk_coupons_max_discount
                             CHECK (
                                 maximum_discount_amount IS NULL
                                     OR maximum_discount_amount > 0
                                 ),

                         INDEX idx_coupons_active_dates (
        active,
        deleted,
        starts_at,
        expires_at
    )
);

CREATE TABLE coupon_redemptions (
                                    id BIGINT NOT NULL AUTO_INCREMENT,

                                    coupon_id BIGINT NOT NULL,

                                    user_id BIGINT NOT NULL,

                                    order_id BIGINT NOT NULL,

                                    discount_amount DECIMAL(12, 2) NOT NULL,

                                    redeemed_at DATETIME(6) NOT NULL,

                                    PRIMARY KEY (id),

                                    CONSTRAINT uk_coupon_redemptions_order
                                        UNIQUE (order_id),

                                    CONSTRAINT fk_coupon_redemptions_coupon
                                        FOREIGN KEY (coupon_id)
                                            REFERENCES coupons(id),

                                    CONSTRAINT fk_coupon_redemptions_user
                                        FOREIGN KEY (user_id)
                                            REFERENCES users(id),

                                    CONSTRAINT fk_coupon_redemptions_order
                                        FOREIGN KEY (order_id)
                                            REFERENCES orders(id),

                                    CONSTRAINT chk_coupon_redemptions_amount
                                        CHECK (discount_amount > 0),

                                    INDEX idx_coupon_redemptions_coupon_user (
        coupon_id,
        user_id
    )
);