package com.ibm.demo.order.DTO;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record UpdateOrderDetailRequest(
        @NotNull(message = "Product ID is required") @Digits(integer = 10, fraction = 0, message = "10 characters max") @Positive(message = "Product ID must be positive") Integer productId,

        @NotNull(message = "Quantity is required") @Digits(integer = 10, fraction = 0, message = "10 characters max") @Positive(message = "Quantity must be positive") Integer quantity) {
}
