package com.ibm.demo.product.DTO.internal;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * 庫存調整請求（內部使用）：將庫存從 {@code from} 的預留狀態調整為 {@code to} 的預留狀態。
 *
 * <p>兩邊都是 {@code @NotNull} 但<b>刻意不是 {@code @NotEmpty}</b>：空集合是有意義的輸入
 * （{@code from} 空 = 純新增預留，{@code to} 空 = 全數歸還），加上 {@code @NotEmpty} 會憑空造出
 * 一種失敗模式而換不到任何保護。
 */
@Builder
@Schema(description = "庫存調整請求（內部使用）：將庫存從 from 的預留狀態調整為 to 的預留狀態")
public record AdjustStockRequest(
    @NotNull(message = "From items are required")
    @Schema(description = "調整前的訂單項目集合（原預留）", requiredMode = Schema.RequiredMode.REQUIRED)
    Set<@Valid OrderItemRequest> from,

    @NotNull(message = "To items are required")
    @Schema(description = "調整後的訂單項目集合（新預留）", requiredMode = Schema.RequiredMode.REQUIRED)
    Set<@Valid OrderItemRequest> to
) {

}
