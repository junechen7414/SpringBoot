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
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.BusinessLogicCheck.BusinessException;
import com.ibm.demo.util.ErrorCode;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = (errorCode != null) ? errorCode.getStatus() : HttpStatus.BAD_REQUEST;
        String errorType = (errorCode != null) ? errorCode.getMessage() : "Business Error";

        return createResponseEntity(status, errorType, ex.getMessage());
    }

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ApiErrorResponse> handleBulkheadFull(BulkheadFullException ex) {
        return createResponseEntity(HttpStatus.SERVICE_UNAVAILABLE, "Service Overloaded", "系統負載過高，請稍後再試。");
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiErrorResponse> handleCallNotPermitted(CallNotPermittedException ex) {
        return createResponseEntity(HttpStatus.SERVICE_UNAVAILABLE, "Circuit Breaker Open", "服務暫時不可用，請稍後再試。");
    }

    // 處理樂觀鎖衝突例外
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        return createResponseEntity(HttpStatus.CONFLICT, "Optimistic Locking Failure", "資料已被其他使用者修改，請重新整理後再試。");
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

        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error("Validation Error")
                .message("參數驗證失敗: " + detailedMessage)
                .build();

        return new ResponseEntity<>(response, status);

    }

    private ResponseEntity<ApiErrorResponse> createResponseEntity(HttpStatus status, String errorType, String message) {
        ApiErrorResponse apiErrorResponse = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(errorType)
                .message(message)
                .build();
        return new ResponseEntity<>(apiErrorResponse, status);
    }
}