
package com.springcommerce.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class OrderCancellationException
        extends ResponseStatusException {

    public OrderCancellationException(Long orderId) {
        super(
                HttpStatus.CONFLICT,
                "Order " + orderId
                        + " cannot be cancelled because it is no longer awaiting processing."
        );
    }
}

