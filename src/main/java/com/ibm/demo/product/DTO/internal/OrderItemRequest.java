package com.ibm.demo.product.DTO.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

/**
 * 庫存變動的單一項目（內部使用）。
 *
 * <p>約束是後來補上的：這個型別只在 order → product 的內部呼叫間流動，上游 DTO
 * （{@code CreateOrderDetailRequest} / {@code UpdateOrderDetailRequest}）本來就擋掉了 null 與非正數，
 * 所以「內部呼叫一定合法」曾經成立 —— 但那是靠人記住，而非靠型別保證。把同樣的約束寫在這裡，
 * 內部端點就不再是驗證的破口。
 */
@Builder
@Schema(description = "訂單項目請求（內部使用）")
public record OrderItemRequest(
        @NotNull(message = "Product ID is required")
        @Positive(message = "Product ID must be positive")
        @Schema(description = "商品 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer productId,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        @Schema(description = "購買數量", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity) {

}

// Made with Bob
