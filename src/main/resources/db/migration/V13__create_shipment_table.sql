CREATE TABLE shipment (
                          id BIGINT NOT NULL AUTO_INCREMENT,
                          order_id BIGINT NOT NULL,
                          carrier VARCHAR(100) NOT NULL,
                          tracking_number VARCHAR(150) NOT NULL,
                          status VARCHAR(30) NOT NULL,
                          shipped_at DATETIME(6) NULL,
                          delivered_at DATETIME(6) NULL,
                          created_at DATETIME(6) NOT NULL,
                          updated_at DATETIME(6) NOT NULL,

                          PRIMARY KEY (id),

                          CONSTRAINT uk_shipment_order
                              UNIQUE (order_id),

                          CONSTRAINT uk_shipment_tracking_number
                              UNIQUE (tracking_number),

                          CONSTRAINT fk_shipment_order
                              FOREIGN KEY (order_id)
                                  REFERENCES orders(id)
);

CREATE INDEX idx_shipment_status
    ON shipment(status);

CREATE INDEX idx_shipment_tracking_number
    ON shipment(tracking_number);