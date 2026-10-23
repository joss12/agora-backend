package com.springcommerce.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(

        @NotBlank(message = "Recipient name is required")
        @Size(max = 100)
        String recipientName,

        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
                message = "Enter a valid Korean mobile phone number"
        )
        String phoneNumber,

        @NotBlank(message = "Postal code is required")
        @Pattern(
                regexp = "^\\d{5}$",
                message = "Korean postal code must contain 5 digits"
        )
        String postalCode,

        @NotBlank(message = "Delivery address is required")
        @Size(max = 255)
        String address,

        @Size(max = 255)
        String addressDetail,

        @Size(max = 500)
        String deliveryInstructions,

        @Size(max = 50)
        String couponCode

) {

        /*
         * Backward-compatible constructor.
         *
         * Existing tests and Java callers can continue
         * creating orders without providing a coupon.
         */
        public CreateOrderRequest(
                String recipientName,
                String phoneNumber,
                String postalCode,
                String address,
                String addressDetail,
                String deliveryInstructions
        ) {
                this(
                        recipientName,
                        phoneNumber,
                        postalCode,
                        address,
                        addressDetail,
                        deliveryInstructions,
                        null
                );
        }
}