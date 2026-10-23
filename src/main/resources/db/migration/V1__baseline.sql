CREATE TABLE IF NOT EXISTS product (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       name VARCHAR(200) NOT NULL,
    sku VARCHAR(100) UNIQUE,
    description TEXT,
    price DECIMAL(12, 2) NOT NULL,
    stock INT,
    created_at DATETIME,
    updated_at DATETIME
    );