CREATE TABLE users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,

                       first_name VARCHAR(100) NOT NULL,
                       last_name VARCHAR(100) NOT NULL,

                       email VARCHAR(255) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,

                       role VARCHAR(20) NOT NULL,
                       enabled BOOLEAN NOT NULL DEFAULT TRUE,

                       created_at DATETIME NOT NULL,
                       updated_at DATETIME NOT NULL
);