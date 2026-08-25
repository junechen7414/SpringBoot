package com.ibm.demo.exception;

import java.util.Objects;

import lombok.Getter;

/**
 * 業務規則拒絕請求時拋出的例外，由 {@code GlobalExceptionHandler} 對應成 {@code ApiErrorResponse}。
 *
 * <p><b>刻意不捕捉 stack trace</b>（{@code writableStackTrace = false}）：這是控制流用的例外
 * —— 庫存不足、查無資源、參數不合法都是業務上完全預期的結果。它的資訊量全在
 * {@link ErrorCode} 與訊息裡，throw 點對排查沒有價值，而 {@code fillInStackTrace()} 正是
 * throw 一個例外的主要成本（驗證失敗與查無資源都走這條高頻路徑）。
 *
 * <p>這也讓「不該有 stack trace」從慣例變成型別保證：就算日後有人手滑寫了
 * {@code log.error(..., ex)}，也印不出上百行框架呼叫鏈。
 *
 * <p><b>前提</b>：目前 {@link ErrorCode} 全部常數皆為 4xx。若日後新增 5xx 的 ErrorCode
 * （代表真的壞了、需要 throw 點資訊），須重新評估這個決定。
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        // 參數依序為 message, cause, enableSuppression, writableStackTrace
        super(message, null, false, false);
        // errorCode 是 handler 決定 status / code / 記錄等級的唯一依據，缺了它不是「退回預設」
        // 而是寫錯了 —— 因此在 throw 點就炸，而不是讓 handler 帶著 null 分支往下走。
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }
}
