package com.springcommerce.backend.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientException;

public class TossPaymentApiException extends RestClientException {

    private final HttpStatusCode statusCode;
    private final String tossErrorCode;

    public TossPaymentApiException(
            HttpStatusCode statusCode,
            String tossErrorCode
    ) {
        super("Toss payment API returned HTTP " + statusCode.value());

        this.statusCode = statusCode;
        this.tossErrorCode = tossErrorCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getTossErrorCode() {
        return tossErrorCode;
    }

    public boolean isServerError() {
        return statusCode.is5xxServerError();
    }

    public boolean isClientError() {
        return statusCode.is4xxClientError();
    }
}