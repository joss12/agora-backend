package com.springcommerce.backend.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(
            Long productId,
            int available,
            int requested
    ) {
        super(
                "Insufficient stock for product " + productId
                + ". Available:" + available
                + ", requested:" + requested
        );
    }
}
