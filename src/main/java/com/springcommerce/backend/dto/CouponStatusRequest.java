package com.springcommerce.backend.dto;

import jakarta.validation.constraints.NotNull;

public record CouponStatusRequest(

        @NotNull
        Boolean active

) {
}
