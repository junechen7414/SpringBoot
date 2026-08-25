package com.ibm.demo;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;

/**
 * 全域例外處理，同時是**唯一**記錄例外的地方。
 *
 * <p>這裡是整個應用例外路徑的單一漏斗，也是唯一知道最終 HTTP status 的位置 —— 因此判斷
 * 「這個例外嚴不嚴重、該記到什麼程度」的責任放在這裡。原本由 AOP（已移除的 LoggingAspect）
 * 記錄例外的做法有兩個結構性缺陷：它只看得到方法簽章、無從判斷嚴重程度，且 pointcut 涵蓋
 * Service 與 Controller 兩層，例外往外傳時每層各印一份完整 stack trace。
 *
 * <p>記錄原則是**預期 vs 未預期**，而非字面上的 4xx vs 5xx：
 * <ul>
 *   <li><b>預期</b>（業務拒絕、流量控制、並發衝突）→ 一行 WARN，不帶 stack trace。
 *       resilience4j 的拒絕雖然是 503/429，仍屬預期 —— 記成 ERROR 會讓系統一飽和就被自己的
 *       ERROR log 洗版，而那正是最需要看清狀況的時刻。</li>
 *   <li><b>未預期</b>（{@link #handleUnexpected}）→ ERROR 並帶完整 stack trace。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        // errorCode 由 BusinessException 建構子以 requireNonNull 保證非 null，此處無須防禦性分支。
        ErrorCode errorCode = ex.getErrorCode();
        return respond(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(), ex.getMessage(), request);
    }

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ApiErrorResponse> handleBulkheadFull(BulkheadFullException ex, HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "BULKHEAD_FULL", "Service Overloaded",
                "系統負載過高，請稍後再試。", request);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiErrorResponse> handleCallNotPermitted(CallNotPermittedException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN", "Circuit Breaker Open",
                "服務暫時不可用，請稍後再試。", request);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimiter(RequestNotPermitted ex, HttpServletRequest request) {
        return respond(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Rate Limit Exceeded",
                "請求過於頻繁，請稍後再試。", request);
    }

    // 處理樂觀鎖衝突例外
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK", "Optimistic Locking Failure",
                "資料已被其他使用者修改，請重新整理後再試。", request);
    }

    /**
     * 未預期的例外：真的壞了、需要有人去看，因此這是**唯一**該印 stack trace 的地方。
     *
     * <p>訊息不回給呼叫端（避免洩漏內部細節），只回統一的 {@link ApiErrorResponse} 格式。
     *
     * <p>注意：{@code ResponseEntityExceptionHandler} 內建處理的那批 Spring MVC 例外仍會優先命中
     * （Spring 選最具體的 handler），不會被這個 catch-all 攔走。但若日後導入方法級 authZ
     * （{@code @PreAuthorize}），{@code AuthorizationDeniedException} 會被這裡吃掉變成 500 ——
     * 屆時須在它之前補一個明確的 handler 以保住 403。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 最後一個參數是 Throwable → SLF4J 會印出完整 stack trace。
        log.error("[UNEXPECTED] 500 {} {}", request.getMethod(), request.getRequestURI(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return new ResponseEntity<>(
                body(status.value(), "Internal Server Error", "系統發生未預期的錯誤，請稍後再試。"), status);
    }

    /**
     * 優化：MethodArgumentNotValidException包含過多不必要資訊也會有回應格式的不一致性前端必須寫2套邏輯來處理錯誤，因此改為統一格式回應給前端，并且將錯誤訊息格式化為更易讀的形式。
     * 例如：參數驗證失敗: [field1: must not be null]; [field2: must be positive]
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detailedMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> String.format("[%s: %s]", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));

        String message = "參數驗證失敗: " + detailedMessage;

        // 這個 override 的回傳型別是父類別規定的 ResponseEntity<Object>，無法直接借用 respond()，
        // 但回應本體一律由 body() 組裝 —— 格式與其他 handler 保證一致。
        if (request instanceof ServletWebRequest servletWebRequest) {
            logExpected(status.value(), "VALIDATION", message, servletWebRequest.getRequest());
        }

        return new ResponseEntity<>(body(status.value(), "Validation Error", message), status);
    }

    /**
     * 預期的例外：記一行、不帶 stack trace，然後組回應。
     *
     * <p>這類例外的資訊量全在 status + tag + 請求路徑 + 訊息裡；stack trace 只會把真正的錯誤淹掉
     */
    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String tag, String errorType, String message,
            HttpServletRequest request) {
        logExpected(status.value(), tag, message, request);
        return new ResponseEntity<>(body(status.value(), errorType, message), status);
    }

    private void logExpected(int status, String tag, String message, HttpServletRequest request) {
        log.warn("[{}] {} {} {} - {}", tag, status, request.getMethod(), request.getRequestURI(), message);
    }

    /** 組裝回應本體的**唯一**入口 —— 所有 handler（含父類別的 override）都經由這裡，格式才不會分岔。 */
    private ApiErrorResponse body(int status, String errorType, String message) {
        return ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(errorType)
                .message(message)
                .build();
    }
}
