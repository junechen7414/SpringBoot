package com.ibm.demo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 單一筆驗證失敗，構成錯誤回應中 {@code errors} 陣列的元素。
 *
 * <p>與其他錯誤型別不同，這個 record <b>會真的被序列化</b>：{@code GlobalExceptionHandler} 把
 * {@code List<ValidationError>} 放進 {@code ProblemDetail} 的 extension，Jackson 依 record component
 * 產出 JSON。因此它同時是文件與 wire format —— 不存在漂移的空間。
 *
 * <p>為什麼需要結構化陣列而不只是一句 {@code detail}：呼叫端（尤其是表單 UI）要把訊息掛回**對應的
 * 輸入欄位**上，靠剖字串做這件事等於把訊息格式變成契約，改個標點就壞。
 *
 * <p>{@code field} 在 class-level（跨欄位）約束下沒有意義 —— 例如「起始日不得晚於結束日」不屬於任何
 * 單一欄位。這種情況 {@code field} 為 null 並由 {@code NON_NULL} 略去，呼叫端看到「沒有 field」就知道
 * 該把訊息顯示在表單層級而非某個輸入框旁。
 *
 * <p>刻意<b>不</b>收 {@code rejectedValue}：驗證失敗的輸入可能正是密碼、身分證號這類不該回傳、也不該
 * 進 log 的值。呼叫端本來就握有自己送出的內容，回傳它並無資訊增益。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ValidationError", description = "單一筆參數驗證失敗")
public record ValidationError(

        @Schema(description = "未通過驗證的欄位名。class-level（跨欄位）約束沒有對應欄位，此時本欄位缺席",
                example = "accountId") String field,

        @Schema(description = "驗證失敗的原因", example = "must not be null") String message) {

    /**
     * 壓成一行人類可讀字串，供 {@code detail} 使用。
     *
     * <p>放在這裡而非 handler 裡：{@code detail} 與 {@code errors} 必須描述同一件事，讓兩者從**同一個
     * 資料來源**產出，就不會出現「陣列裡有三筆、detail 只寫了兩筆」這種自相矛盾。
     */
    public String describe() {
        return field == null ? message : field + " " + message;
    }
}
