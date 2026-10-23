package com.springcommerce.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.springcommerce.backend.config.TossPaymentProperties;
import com.springcommerce.backend.exception.TossPaymentApiException;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TossPaymentClient {

    private static final Pattern ERROR_CODE_PATTERN =
            Pattern.compile(
                    "\"code\"\\s*:\\s*\"([A-Za-z0-9_\\-]+)\""
            );

    private final RestClient restClient;
    private final TossPaymentProperties properties;

    public TossPaymentClient(
            TossPaymentProperties properties
    ) {
        this.properties = properties;

        var httpClient = java.net.http.HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        var requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                Duration.ofSeconds(10)
        );

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultStatusHandler(
                        HttpStatusCode::isError,
                        (request, response) -> {

                            String errorCode = null;

                            try {
                                byte[] responseBytes =
                                        response.getBody()
                                                .readNBytes(8192);

                                String responseBody =
                                        new String(
                                                responseBytes,
                                                StandardCharsets.UTF_8
                                        );

                                Matcher matcher =
                                        ERROR_CODE_PATTERN.matcher(
                                                responseBody
                                        );

                                if (matcher.find()) {
                                    errorCode = matcher.group(1);
                                }

                            } catch (Exception ignored) {
                                // Do not expose raw gateway responses.
                            }

                            throw new TossPaymentApiException(
                                    response.getStatusCode(),
                                    errorCode
                            );
                        }
                )
                .build();
    }

    // ============================================================
    // CONFIRM TOSS PAYMENT
    // ============================================================

    public TossPaymentResult confirm(
            String paymentKey,
            String orderId,
            long amount,
            String idempotencyKey
    ) {
        return restClient.post()
                .uri("/v1/payments/confirm")
                .header(
                        "Authorization",
                        authorizationHeader()
                )
                .header(
                        "Idempotency-Key",
                        idempotencyKey
                )
                .body(
                        Map.of(
                                "paymentKey", paymentKey,
                                "orderId", orderId,
                                "amount", amount
                        )
                )
                .retrieve()
                .body(TossPaymentResult.class);
    }

    // ============================================================
    // GET TOSS PAYMENT BY PAYMENT KEY
    // ============================================================

    public TossPaymentResult getPayment(
            String paymentKey
    ) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Toss payment key is required"
            );
        }

        return restClient.get()
                .uri(
                        "/v1/payments/{paymentKey}",
                        paymentKey
                )
                .header(
                        "Authorization",
                        authorizationHeader()
                )
                .retrieve()
                .body(TossPaymentResult.class);
    }

    // ============================================================
    // GET TOSS PAYMENT BY ORDER ID
    // ============================================================

    public TossPaymentResult getPaymentByOrderId(
            String orderId
    ) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException(
                    "Toss order ID is required"
            );
        }

        return restClient.get()
                .uri(
                        "/v1/payments/orders/{orderId}",
                        orderId
                )
                .header(
                        "Authorization",
                        authorizationHeader()
                )
                .retrieve()
                .body(TossPaymentResult.class);
    }

    // ============================================================
    // CANCEL / FULLY REFUND TOSS PAYMENT
    // ============================================================

    public TossPaymentResult cancelPayment(
            String paymentKey,
            String cancelReason,
            String idempotencyKey
    ) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Toss payment key is required"
            );
        }

        if (cancelReason == null || cancelReason.isBlank()) {
            throw new IllegalArgumentException(
                    "Cancellation reason is required"
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required"
            );
        }

        return restClient.post()
                .uri(
                        "/v1/payments/{paymentKey}/cancel",
                        paymentKey
                )
                .header(
                        "Authorization",
                        authorizationHeader()
                )
                .header(
                        "Idempotency-Key",
                        idempotencyKey
                )
                .body(
                        Map.of(
                                "cancelReason",
                                cancelReason
                        )
                )
                .retrieve()
                .body(TossPaymentResult.class);
    }

    // ============================================================
    // BASIC AUTHENTICATION
    // ============================================================

    private String authorizationHeader() {

        String secretKey = properties.getSecretKey();

        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "TOSS_SECRET_KEY is not configured"
            );
        }

        String credentials = secretKey + ":";

        String encoded = Base64.getEncoder()
                .encodeToString(
                        credentials.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        return "Basic " + encoded;
    }

    // ============================================================
    // TOSS PAYMENT RESPONSE
    // ============================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossPaymentResult(
            String paymentKey,
            String orderId,
            Long totalAmount,
            String status,
            Long balanceAmount
    ) {

        // Backward compatibility with existing tests and services.
        public TossPaymentResult(
                String paymentKey,
                String orderId,
                Long totalAmount,
                String status
        ) {
            this(
                    paymentKey,
                    orderId,
                    totalAmount,
                    status,
                    null
            );
        }
    }
}