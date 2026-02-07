package com.ibm.demo.order.DTO;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderRequest {
    @NotNull(message = "Order ID is required")
    @Digits(integer = 10, fraction = 0, message = "10 characters max")
    @Positive(message = "Order ID must be positive")
    private Integer orderId;

    @NotNull(message = "orderStatus is required")
    @Digits(integer = 4, fraction = 0, message = "4 characters max")
    @Positive(message = "orderStatus must be positive")
    private Integer orderStatus;

    @Valid
    @NotEmpty(message = "Order items are required")    
    private List<UpdateOrderDetailRequest> items;
}
