package com.ibm.demo.order.DTO;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "訂單列表回應")
public record GetOrderListResponse(
        @Schema(description = "訂單 ID", example = "1")
        Integer orderId,

        @Schema(description = "訂單狀態 (1001=訂單建立, 1003=訂單取消)", example = "1001")
        Integer status,

        @Schema(description = "訂單總金額", example = "500.00")
        BigDecimal totalAmount) {
}
