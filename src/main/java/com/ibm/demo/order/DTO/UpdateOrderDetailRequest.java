package com.ibm.demo.order.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
@Schema(description = "更新訂單明細請求")
public record UpdateOrderDetailRequest(
        @NotNull(message = "Product ID is required")
        @Digits(integer = 10, fraction = 0, message = "10 characters max")
        @Positive(message = "Product ID must be positive")
        @Schema(description = "商品 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer productId,

        @NotNull(message = "Quantity is required")
        @Digits(integer = 10, fraction = 0, message = "10 characters max")
        @Positive(message = "Quantity must be positive")
        @Schema(description = "購買數量", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity) {
}
