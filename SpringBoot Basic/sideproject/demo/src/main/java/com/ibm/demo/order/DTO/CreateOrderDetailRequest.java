package com.ibm.demo.order.DTO;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderDetailRequest {
    @NotNull(message = "Product ID is required")
    @Digits(integer = 10, fraction = 0, message = "10 characters max")
    @Positive(message = "Product ID must be positive")
    private Integer productId;

    @NotNull(message = "Quantity is required")
    @Digits(integer = 10, fraction = 0, message = "10 characters max")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

}
