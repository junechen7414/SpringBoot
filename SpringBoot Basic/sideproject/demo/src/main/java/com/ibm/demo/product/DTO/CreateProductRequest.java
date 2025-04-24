package com.ibm.demo.product.DTO;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "100 characters max")
    private String name;

    @NotNull(message = "Price is required")
    @Digits(integer = 8, fraction = 4)
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotNull(message = "Stock Qty is required")
    @Digits(integer = 10, fraction = 0)
    @Positive(message = "Stock Qty must be positive")
    private int stockQty;
}
