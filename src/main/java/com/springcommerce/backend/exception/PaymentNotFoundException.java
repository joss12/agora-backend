
package com.springcommerce.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PaymentNotFoundException
        extends ResponseStatusException {

    public PaymentNotFoundException(Long orderId) {
        super(
                HttpStatus.NOT_FOUND,
                "Payment not found for order " + orderId
        );
    }
}

