package com.ibm.demo.order.DTO;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "訂單明細項目")
public record OrderItemDTO(
        @Schema(description = "商品 ID", example = "1")
        Integer productId,

        @Schema(description = "商品名稱", example = "商品A")
        String productName,

        @Schema(description = "購買數量", example = "2")
        Integer quantity,

        @Schema(description = "商品單價", example = "250.00")
        BigDecimal productPrice) {
}
