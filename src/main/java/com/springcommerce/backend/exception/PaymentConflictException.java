
package com.springcommerce.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PaymentConflictException
        extends ResponseStatusException {

    public PaymentConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
