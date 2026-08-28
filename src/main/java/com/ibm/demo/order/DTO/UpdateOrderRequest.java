package com.ibm.demo.order.DTO;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

/**
 * 更新訂單請求。
 *
 * <p><b>不含 {@code orderId}</b>：要更新哪一筆是由 URI（{@code PUT /order/{orderId}}）指定的。
 * 兩邊都放會製造「path 與 body 不一致時聽誰的」這種只能靠額外規則回答的問題，而那個規則無論
 * 怎麼訂都是多出來的複雜度。
 */
@Builder
@Schema(description = "更新訂單請求")
public record UpdateOrderRequest(
        @NotNull(message = "Order Status is required")
        @Digits(integer = 4, fraction = 0, message = "4 characters max")
        @Positive(message = "Order Status must be positive")
        @Schema(description = "訂單狀態 (1001=訂單建立, 1003=訂單取消)", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer orderStatus,

        @NotEmpty(message = "Order items are required")
        @Schema(description = "訂單明細項目列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@Valid UpdateOrderDetailRequest> items) {
}
