package com.ibm.demo.product.DTO;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateProductRequest(
                @NotNull(message = "Id is required") @Positive(message = "Id must be positive") @Digits(integer = 10, fraction = 0) Integer id,

                @NotBlank(message = "Name is required") @Size(max = 100, message = "100 characters max") String name,

                @NotNull(message = "Price is required") @Digits(integer = 8, fraction = 4) @Positive(message = "Price must be positive") BigDecimal price,

                @NotNull(message = "Sale Status is required") @Digits(integer = 4, fraction = 0) Integer saleStatus,

                @NotNull(message = "Stock Qty is required") @Digits(integer = 10, fraction = 0) Integer stockQty) {
}