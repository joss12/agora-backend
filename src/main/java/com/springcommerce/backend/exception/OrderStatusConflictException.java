
package com.springcommerce.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class OrderStatusConflictException
        extends ResponseStatusException {

    public OrderStatusConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
