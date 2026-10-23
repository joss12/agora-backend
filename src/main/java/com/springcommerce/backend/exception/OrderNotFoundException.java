package com.springcommerce.backend.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long id) {

        super("Order not found with id: " + id);
    }
}
