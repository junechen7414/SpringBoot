package com.ibm.demo.product.DTO.internal;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "庫存調整請求（內部使用）：將庫存從 from 的預留狀態調整為 to 的預留狀態")
public record AdjustStockRequest(
    @Schema(description = "調整前的訂單項目集合（原預留）")
    Set<OrderItemRequest> from,

    @Schema(description = "調整後的訂單項目集合（新預留）")
    Set<OrderItemRequest> to
) {

}
