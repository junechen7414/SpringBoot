package com.ibm.demo.product.DTO.internal;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "處理訂單項目請求（內部使用）")
public record ProcessOrderItemsRequest(
    @Schema(description = "原始訂單項目集合")
    Set<OrderItemRequest> originalItems,
    
    @Schema(description = "更新後的訂單項目集合")
    Set<OrderItemRequest> updatedItems
) {
    
}

// Made with Bob
