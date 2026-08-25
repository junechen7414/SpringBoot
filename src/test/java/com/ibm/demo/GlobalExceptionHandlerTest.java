package com.ibm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;

public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    // handler 用它記一行 WARN（method + path）；本測試只驗回應，故任一路徑皆可。
    private MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

    @Test
    @DisplayName("處理 BulkheadFullException，應回傳 503 Service Unavailable 狀態碼")
    void handleBulkheadFull_ShouldReturnServiceUnavailableStatus() {
        // Arrange
        BulkheadFullException ex = BulkheadFullException.createBulkheadFullException(
                io.github.resilience4j.bulkhead.Bulkhead.ofDefaults("test-bulkhead"));

        // Act
        ResponseEntity<ApiErrorResponse> responseEntity = globalExceptionHandler.handleBulkheadFull(ex, request);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(responseEntity.getBody().code()).isEqualTo("BULKHEAD_FULL");
        assertThat(responseEntity.getBody().error()).isEqualTo("Service Overloaded");
        assertThat(responseEntity.getBody().message()).isEqualTo("系統負載過高，請稍後再試。");
    }

    @Test
    @DisplayName("處理樂觀鎖衝突例外，應回傳 409 Conflict 狀態碼")
    void handleOptimisticLockingFailure_ShouldReturnConflictStatus() {
        // Arrange
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException("Test Entity", 1L);

        // Act
        ResponseEntity<ApiErrorResponse> responseEntity = globalExceptionHandler.handleOptimisticLockingFailure(ex, request);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(responseEntity.getBody().code()).isEqualTo("OPTIMISTIC_LOCK");
        assertThat(responseEntity.getBody().error()).isEqualTo("Optimistic Locking Failure");
        assertThat(responseEntity.getBody().message()).isEqualTo("資料已被其他使用者修改，請重新整理後再試。");
    }


    @Test
    @DisplayName("處理 CallNotPermittedException，應回傳 503 Service Unavailable 狀態碼")
    void handleCallNotPermitted_ShouldReturnServiceUnavailableStatus() {
        // Arrange
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test-circuit-breaker"));

        // Act
        ResponseEntity<ApiErrorResponse> responseEntity = globalExceptionHandler.handleCallNotPermitted(ex, request);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(responseEntity.getBody().code()).isEqualTo("CIRCUIT_OPEN");
        assertThat(responseEntity.getBody().error()).isEqualTo("Circuit Breaker Open");
        assertThat(responseEntity.getBody().message()).isEqualTo("服務暫時不可用，請稍後再試。");
    }

    @Test
    @DisplayName("處理 RequestNotPermitted，應回傳 429 Too Many Requests 狀態碼")
    void handleRateLimiter_ShouldReturnTooManyRequestsStatus() {
        // Arrange
        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(
                io.github.resilience4j.ratelimiter.RateLimiter.ofDefaults("test-rate-limiter"));

        // Act
        ResponseEntity<ApiErrorResponse> responseEntity = globalExceptionHandler.handleRateLimiter(ex, request);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(responseEntity.getBody().code()).isEqualTo("RATE_LIMITED");
        assertThat(responseEntity.getBody().error()).isEqualTo("Rate Limit Exceeded");
        assertThat(responseEntity.getBody().message()).isEqualTo("請求過於頻繁，請稍後再試。");
    }

    @Test
    @DisplayName("處理 BusinessException，應由 ErrorCode 決定狀態碼，並把錯誤碼放進 code 欄位")
    void handleBusinessException_ShouldMapErrorCodeToStatusAndCode() {
        // Arrange
        BusinessException ex = new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH, "商品 5 庫存不足（需要 10、剩 3）");

        // Act
        ResponseEntity<ApiErrorResponse> responseEntity = globalExceptionHandler.handleBusinessException(ex, request);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        // code 是機器可讀識別碼、error 是類型標籤、message 是這次請求的細節 —— 三者不可混用
        assertThat(responseEntity.getBody().code()).isEqualTo("PRODUCT_003");
        assertThat(responseEntity.getBody().error()).isEqualTo("商品庫存不足");
        assertThat(responseEntity.getBody().message()).isEqualTo("商品 5 庫存不足（需要 10、剩 3）");
    }

    @Test
    @DisplayName("BusinessException 建構時 errorCode 為 null 應立刻拋 NPE，不讓 null 流進 handler")
    void businessException_ShouldRejectNullErrorCode() {
        // handler 之所以能不寫 errorCode != null 的防禦分支，靠的就是這個建構子契約。
        assertThatNullPointerException()
                .isThrownBy(() -> new BusinessException(null, "任意訊息"));
    }

    @Test
    @DisplayName("處理欄位驗證失敗，應與其他 handler 共用同一個回應格式（code = VALIDATION）")
    void handleMethodArgumentNotValid_ShouldUseSharedResponseFormat() throws Exception {
        // Arrange：組一個帶兩個欄位錯誤的 BindingResult
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("methodParameterSource", String.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "createOrderRequest");
        bindingResult.addError(new FieldError("createOrderRequest", "accountId", "must not be null"));
        bindingResult.addError(new FieldError("createOrderRequest", "items", "must not be empty"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleMethodArgumentNotValid(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(request));

        // Assert：這條路徑以前自己組回應、格式與其他 handler 分岔，現在必須是同一個 ApiErrorResponse
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseEntity.getBody()).isInstanceOf(ApiErrorResponse.class);
        ApiErrorResponse body = (ApiErrorResponse) responseEntity.getBody();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.code()).isEqualTo("VALIDATION");
        assertThat(body.error()).isEqualTo("Validation Error");
        assertThat(body.message())
                .isEqualTo("參數驗證失敗: [accountId: must not be null]; [items: must not be empty]");
    }

    /** 僅供建構 {@link MethodParameter} 使用 —— MethodArgumentNotValidException 需要一個真實的方法參數。 */
    @SuppressWarnings("unused")
    private void methodParameterSource(String argument) {
        // 刻意留空
    }

}
