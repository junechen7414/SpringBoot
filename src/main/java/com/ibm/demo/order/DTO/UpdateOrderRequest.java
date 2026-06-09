package com.ibm.demo.order.DTO;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
@Schema(description = "更新訂單請求")
public record UpdateOrderRequest(
        @NotNull(message = "Order ID is required")
        @Digits(integer = 10, fraction = 0, message = "10 characters max")
        @Positive(message = "Order ID must be positive")
        @Schema(description = "訂單 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer orderId,

        @NotNull(message = "Order Status is required")
        @Digits(integer = 4, fraction = 0, message = "4 characters max")
        @Positive(message = "Order Status must be positive")
        @Schema(description = "訂單狀態 (1001=訂單建立, 1003=訂單取消)", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer orderStatus,

        @Valid
        @NotEmpty(message = "Order items are required")
        @Schema(description = "訂單明細項目列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UpdateOrderDetailRequest> items) {
}
