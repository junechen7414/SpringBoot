package com.ibm.demo.product.DTO;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * 更新商品請求。刻意不含 saleStatus —— 下架（Delist）只走 {@code DELETE /product/{id}}，
 * 見 {@code docs/adr/0001-soft-delete-only-for-termination.md}。
 */
@Builder
@Schema(description = "更新商品請求")
public record UpdateProductRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "100 characters max")
        @Schema(description = "商品名稱", example = "商品A", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotNull(message = "Price is required")
        @Digits(integer = 8, fraction = 4)
        @Positive(message = "Price must be positive")
        @Schema(description = "商品價格", example = "250.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal price,

        @NotNull(message = "Stock Qty is required")
        @Digits(integer = 10, fraction = 0)
        @Schema(description = "可用庫存數量", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer available) {
}
