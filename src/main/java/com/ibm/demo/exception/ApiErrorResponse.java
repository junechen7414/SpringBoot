package com.ibm.demo.exception;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;

/**
 * 所有錯誤回應的統一格式，由 {@code GlobalExceptionHandler} 單一組裝。
 *
 * <p><b>{@code code} 與 {@code error} 的分工</b>（兩者刻意分開，別合併）：
 * <ul>
 *   <li>{@code code} —— <b>機器可讀</b>的穩定識別碼（{@code PRODUCT_003}、{@code RATE_LIMITED}）。
 *       呼叫端要分辨「是哪一種錯誤」只能靠它：{@code status} 太粗（同一個 400 有五種原因），
 *       {@code message} 是給人看的、隨時會改字。</li>
 *   <li>{@code error} —— <b>給人看</b>的錯誤類型標籤，來自 {@link ErrorCode#getMessage()}
 *       或 handler 的固定字串，可直接顯示。</li>
 *   <li>{@code message} —— 該次請求的具體細節（帶 ID、數量等），來自 throw 點。</li>
 * </ul>
 */
@Builder
public record ApiErrorResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime timestamp,
        int status,
        String code,
        String error,
        String message) {
}
