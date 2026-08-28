package com.ibm.demo.product.DTO.internal;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

/**
 * 單向庫存變動請求（內部使用）：{@code /product/reserve} 與 {@code /product/release} 共用。
 *
 * <p><b>為什麼要這層包裝</b>：原本這兩個端點的 request body 是裸的 {@code Set<OrderItemRequest>}。
 * 裸集合有兩個問題 ——
 * <ol>
 *   <li><b>驗證進不去</b>：{@code @Valid @RequestBody Set<...>} 走的是 DataBinder 對「整個集合物件」
 *       做驗證，集合本身沒有約束可言，元素上的約束不會被套用。包成具名物件後，
 *       {@code Set<@Valid OrderItemRequest>} 就是與 {@code CreateOrderRequest.items} 完全相同的
 *       尋常 cascade 情形，元素約束確實生效。</li>
 *   <li><b>無法相容擴充</b>：日後要多帶一個欄位（例如 correlation id）就是破壞性變更。</li>
 * </ol>
 *
 * <p>reserve 與 release 共用同一型別而不各寫一個同構的 record：兩者的請求形狀確實相同，
 * 拆成兩個只會讓「它們必須保持一致」變成靠人維護的巧合。方向由端點（URI）表達，不由型別表達。
 */
@Builder
@Schema(description = "庫存變動請求（內部使用）：預留或釋放指定項目的庫存")
public record StockChangeRequest(

        @NotEmpty(message = "Stock change items are required")
        @Schema(description = "要變動庫存的訂單項目集合", requiredMode = Schema.RequiredMode.REQUIRED)
        Set<@Valid OrderItemRequest> items) {
}
