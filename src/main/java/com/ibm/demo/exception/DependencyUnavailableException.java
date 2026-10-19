package com.ibm.demo.exception;

import java.util.Objects;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 下游依賴暫時不可用（503）：{@code *Client} 收到下游回報「滿載」或「熔斷中」，由
 * {@code GlobalExceptionHandler} 對外轉成同樣語意的 503，而不是壓成不透明的 500。
 *
 * <p><b>為什麼不是 {@link BusinessException}：</b>後者的前提是「呼叫端做錯事（4xx）」，建構子會拒絕
 * 非 4xx 的碼，且 CircuitBreaker 設定把它列為忽略。下游過載是依賴<b>真的出問題</b>，必須計入
 * 故障率 —— 改善對外 status 不代表把依賴故障改算成功。本型別不在 {@code ignore-exceptions} 內，
 * 因此預設就會被計為 failure，不需要動 {@code application.yml}。
 *
 * <p><b>為什麼不是 {@link SystemException}：</b>後者對外一律是不透明的 500，呼叫端無從得知「稍後再試
 * 就可能成功」。這裡的 {@link ErrorCode} 與 detail 都由本地決定（不轉送下游文字），所以可以安全公開。
 */
@Getter
public class DependencyUnavailableException extends RuntimeException {

    private final ErrorCode errorCode;

    public DependencyUnavailableException(ErrorCode errorCode, String message) {
        super(message);
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (errorCode.getStatus() != HttpStatus.SERVICE_UNAVAILABLE) {
            throw new IllegalArgumentException(
                    "DependencyUnavailableException 只能承載 503 的 ErrorCode，收到 " + errorCode.getCode()
                            + "（" + errorCode.getStatus() + "）");
        }
        this.errorCode = errorCode;
    }
}
