package com.ibm.demo.order.DTO;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "訂單詳細資訊回應")
public record GetOrderDetailResponse(
        @Schema(description = "帳戶 ID", example = "1")
        Integer accountId,

        @Schema(description = "訂單狀態 (1001=訂單建立, 1003=訂單取消)", example = "1001")
        Integer orderStatus,

        @Schema(description = "訂單總金額", example = "500.00")
        BigDecimal totalAmount,

        @Schema(description = "訂單明細項目列表")
        List<OrderItemDTO> items) {
}
