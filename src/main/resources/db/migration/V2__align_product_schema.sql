-- Product descriptions can be longer than VARCHAR(255)
ALTER TABLE product
    MODIFY COLUMN description TEXT;

-- Protect existing data before making stock NOT NULL
UPDATE product
SET stock = 0
WHERE stock IS NULL;

ALTER TABLE product
    MODIFY COLUMN stock INT NOT NULL;

-- Backfill timestamps for products created before timestamps existed
UPDATE product
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

UPDATE product
SET updated_at = CURRENT_TIMESTAMP
WHERE updated_at IS NULL;

ALTER TABLE product
    MODIFY COLUMN created_at DATETIME NOT NULL,
    MODIFY COLUMN updated_at DATETIME NOT NULL;