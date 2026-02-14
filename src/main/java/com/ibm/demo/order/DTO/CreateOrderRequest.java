package com.ibm.demo.order.DTO;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record CreateOrderRequest(

        @NotNull(message = "Account ID is required") @Digits(integer = 10, fraction = 0, message = "10 characters max") @Positive(message = "Account ID must be positive") Integer accountId,

        @Valid @NotEmpty(message = "Order details are required") List<CreateOrderDetailRequest> orderDetails

) {
}
