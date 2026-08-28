package com.ibm.demo.exception;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 錯誤回應的 <b>OpenAPI schema 宣告</b>。本 record <b>不參與執行期序列化</b> —— 實際寫到線路上的是
 * Spring 的 {@link org.springframework.http.ProblemDetail}（RFC 9457，{@code application/problem+json}），
 * 由 {@code GlobalExceptionHandler} 單一組裝。
 *
 * <p><b>為什麼需要這個「影子」型別</b>：springdoc 若直接吃 {@code ProblemDetail}，extension 欄位
 * （這裡是 {@code code}）藏在 {@code Map<String, Object> properties} 裡，產出的 schema 只會有一個
 * 型別不明的 map，呼叫端與下游 codegen 都拿不到 {@code code}。因此手寫一份與線路格式一致的宣告。
 *
 * <p><b>影子型別的風險是漂移</b>（改了 handler 忘了改這裡，文件就開始說謊），所以由契約測試把這個
 * record 的 component 名稱與真實回應的 JSON 頂層 key 逐一比對 —— 漂移會讓測試紅，而不是等下游踩到。
 *
 * <p>欄位分工（{@code type} / {@code code} 為何都留著，見 {@code GlobalExceptionHandler} 的類別註解）：
 * <ul>
 *   <li>{@code type} —— 問題類型的穩定 URI，由 {@code code} 機械推導。</li>
 *   <li>{@code title} —— 這「類」問題的人類可讀摘要，同一個 code 永遠同一句話。</li>
 *   <li>{@code status} —— HTTP 狀態碼，與 response line 相同（便於 log 與轉貼）。</li>
 *   <li>{@code detail} —— <b>這一次</b>請求的具體說明（帶 ID、數量等），來自 throw 點。</li>
 *   <li>{@code instance} —— 出錯的請求路徑，由框架自動填入。</li>
 *   <li>{@code code} —— <b>機器可讀</b>的穩定識別碼，呼叫端唯一該用來分流的欄位：
 *       {@code status} 太粗（同一個 400 有五種原因），{@code detail} 是給人看的、隨時會改字。</li>
 *   <li>{@code errors} —— 逐筆的欄位驗證失敗，<b>僅在 {@code code} 為 {@code VALIDATION_FAILED} 時出現</b>。
 *       其餘錯誤沒有「哪個欄位」的概念，欄位缺席而非給空陣列。</li>
 * </ul>
 */
@Schema(name = "ApiErrorResponse", description = "錯誤回應（RFC 9457 application/problem+json）")
public record ApiErrorResponse(

        @Schema(description = "問題類型的穩定識別 URI，由 code 推導而來",
                example = "urn:problem:product-stock-not-enough") String type,

        @Schema(description = "這類問題的人類可讀摘要；同一個 code 永遠相同",
                example = "商品庫存不足") String title,

        @Schema(description = "HTTP 狀態碼", example = "400") int status,

        @Schema(description = "本次請求的具體說明", example = "商品 5 庫存不足（需要 10、剩 3）") String detail,

        @Schema(description = "出錯的請求路徑", example = "/product/5/stock") String instance,

        @Schema(description = "機器可讀的穩定錯誤碼，呼叫端應以此分流",
                example = "PRODUCT_STOCK_NOT_ENOUGH") String code,

        @Schema(description = "逐筆的參數驗證失敗；僅 code = VALIDATION_FAILED 時出現，"
                + "其餘錯誤此欄位缺席") List<ValidationError> errors) {
}
