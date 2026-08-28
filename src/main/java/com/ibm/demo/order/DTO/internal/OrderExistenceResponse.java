package com.ibm.demo.order.DTO.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 「該帳戶是否仍有有效訂單」的查詢結果（內部使用）。
 *
 * <p><b>為什麼不回裸的 {@code Boolean}</b>：
 * <ul>
 *   <li>線路上的 {@code true} 沒有說自己是「什麼」為真 —— 讀 log 或抓封包時得回頭翻文件；
 *       {@code {"hasActiveOrder": true}} 自己說得清楚。</li>
 *   <li>裸 {@code Boolean} 在呼叫端幾乎必然寫成 {@code if (client.xxx(id))}，中間隱含一次
 *       <b>原始碼上看不見的 auto-unboxing</b>；body 缺失時就是一個沒有訊息的 NPE。改成 DTO 後
 *       「取出布林值」是明寫的一步（{@code resp.hasActiveOrder()}），且元件型別是原始 {@code boolean}，
 *       不存在半途變 null 的中間狀態。</li>
 *   <li>日後要一併回傳筆數或訂單 ID 清單時，是相容擴充而非破壞性變更。</li>
 * </ul>
 *
 * <p>此端點的成功回應必須有 body —— {@code true} 與 {@code false} 都是「查詢成功」的答案，
 * 與 {@code /account/{id}/order-eligibility}（不合格就拋錯，因此成功時無資訊可回、用 204）
 * 是刻意不同的形狀。
 */
@Builder
@Schema(description = "帳戶訂單存在性查詢結果（內部使用）")
public record OrderExistenceResponse(

        @Schema(description = "該帳戶是否仍有未軟刪除且狀態為 1001 (CREATED) 的訂單",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasActiveOrder) {
}
