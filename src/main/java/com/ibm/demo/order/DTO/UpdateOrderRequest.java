package com.ibm.demo.order.DTO;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

/**
 * 更新訂單請求。
 *
 * <p><b>不含 {@code orderId}</b>：要更新哪一筆是由 URI（{@code PUT /order/{orderId}}）指定的。
 * 兩邊都放會製造「path 與 body 不一致時聽誰的」這種只能靠額外規則回答的問題，而那個規則無論
 * 怎麼訂都是多出來的複雜度。
 *
 * <p><b>不含訂單狀態</b>：取消訂單只走 {@code DELETE /order/{orderId}}（會一併釋放保留庫存）。
 * 若允許 {@code PUT} 改狀態，就多一條「讓訂單消失卻不釋放庫存」的路。
 */
@Builder
@Schema(description = "更新訂單請求")
public record UpdateOrderRequest(
        @NotEmpty(message = "Order items are required")
        @Schema(description = "訂單明細項目列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@Valid UpdateOrderDetailRequest> items) {
}
