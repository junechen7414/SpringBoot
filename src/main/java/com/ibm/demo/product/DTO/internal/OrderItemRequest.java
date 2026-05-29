package com.ibm.demo.product.DTO.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "訂單項目請求（內部使用）")
public record OrderItemRequest(
        @Schema(description = "商品 ID", example = "1")
        Integer productId,
        
        @Schema(description = "購買數量", example = "2")
        Integer quantity) {

}

// Made with Bob
