package com.ibm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;
import com.ibm.demo.exception.ValidationError;

/**
 * {@code GlobalExceptionHandler} 的單元測試：直接呼叫 handler、斷言 {@link ProblemDetail} 的欄位。
 *
 * <p>與 {@code contract.ApiErrorContractTest} 的分工：這裡驗「handler 決定了什麼」（status、title、
 * detail、code），契約測試驗「呼叫端實際收到什麼位元組」（欄位名、Content-Type、instance）。
 * {@code instance} 不在這裡斷言 —— 它由 Spring 的回傳值處理器在寫回應時才填，單元測試看到的必然是
 * null，硬要在這層驗只會驗到假的東西。
 *
 * <p>handler 直接 {@code new} 就能測 —— 它不依賴任何注入的協作者，只需要一個 {@link ServletWebRequest}。
 */
public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    // handler 用它記一行 WARN（method + path）；本測試只驗回應，故任一路徑皆可。
    private MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

    /**
     * handler 一律收 {@code WebRequest} —— 與父類別內建的那批 handler 同一個型別，所有回應才能共用
     * {@code handleExceptionInternal} 這個收尾點。這裡沒有 response 物件，故「回應已 commit」的
     * 判斷會直接跳過。
     */
    private ServletWebRequest webRequest = new ServletWebRequest(request);

    @Test
    @DisplayName("處理 BulkheadFullException，應回傳 503 Service Unavailable 狀態碼")
    void handleBulkheadFull_ShouldReturnServiceUnavailableStatus() {
        // Arrange
        BulkheadFullException ex = BulkheadFullException.createBulkheadFullException(
                io.github.resilience4j.bulkhead.Bulkhead.ofDefaults("test-bulkhead"));

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleBulkheadFull(ex, webRequest);

        // Assert
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ProblemDetail body = problemOf(responseEntity);
        assertThat(body.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(codeOf(body)).isEqualTo("BULKHEAD_FULL");
        assertThat(body.getTitle()).isEqualTo("系統負載過高");
        assertThat(body.getDetail()).isEqualTo("系統負載過高，請稍後再試。");
    }

    @Test
    @DisplayName("處理樂觀鎖衝突例外，應回傳 409 Conflict 狀態碼")
    void handleOptimisticLockingFailure_ShouldReturnConflictStatus() {
        // Arrange
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException("Test Entity", 1L);

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleOptimisticLockingFailure(ex, webRequest);

        // Assert
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = problemOf(responseEntity);
        assertThat(body.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(codeOf(body)).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        assertThat(body.getTitle()).isEqualTo("資料版本衝突");
        assertThat(body.getDetail()).isEqualTo("資料已被其他使用者修改，請重新整理後再試。");
    }

    @Test
    @DisplayName("處理 CallNotPermittedException，應回傳 503 Service Unavailable 狀態碼")
    void handleCallNotPermitted_ShouldReturnServiceUnavailableStatus() {
        // Arrange
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test-circuit-breaker"));

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleCallNotPermitted(ex, webRequest);

        // Assert
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ProblemDetail body = problemOf(responseEntity);
        assertThat(body.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(codeOf(body)).isEqualTo("CIRCUIT_OPEN");
        assertThat(body.getTitle()).isEqualTo("服務暫時不可用");
        assertThat(body.getDetail()).isEqualTo("服務暫時不可用，請稍後再試。");
    }

    @Test
    @DisplayName("處理 RequestNotPermitted，應回傳 429 Too Many Requests 狀態碼")
    void handleRateLimiter_ShouldReturnTooManyRequestsStatus() {
        // Arrange
        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(
                io.github.resilience4j.ratelimiter.RateLimiter.ofDefaults("test-rate-limiter"));

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleRateLimiter(ex, webRequest);

        // Assert
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        ProblemDetail body = problemOf(responseEntity);
        assertThat(body.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(codeOf(body)).isEqualTo("RATE_LIMITED");
        assertThat(body.getTitle()).isEqualTo("請求過於頻繁");
        assertThat(body.getDetail()).isEqualTo("請求過於頻繁，請稍後再試。");
    }

    @Test
    @DisplayName("處理 BusinessException，應由 ErrorCode 決定狀態碼，並把錯誤碼放進 code 欄位")
    void handleBusinessException_ShouldMapErrorCodeToStatusAndCode() {
        // Arrange
        BusinessException ex = new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH, "商品 5 庫存不足（需要 10、剩 3）");

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleBusinessException(ex, webRequest);

        // Assert
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = problemOf(responseEntity);
        assertThat(body.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        // code 是機器可讀識別碼、title 是這「類」問題的固定標籤、detail 是這「次」請求的細節 —— 三者不可混用
        assertThat(codeOf(body)).isEqualTo("PRODUCT_STOCK_NOT_ENOUGH");
        assertThat(body.getTitle()).isEqualTo("商品庫存不足");
        assertThat(body.getDetail()).isEqualTo("商品 5 庫存不足（需要 10、剩 3）");
        // type 與 code 同源推導，兩者結構上不可能不一致
        assertThat(body.getType()).hasToString("urn:problem:product-stock-not-enough");
    }

    @Test
    @DisplayName("BusinessException 建構時 errorCode 為 null 應立刻拋 NPE，不讓 null 流進 handler")
    void businessException_ShouldRejectNullErrorCode() {
        // handler 之所以能不寫 errorCode != null 的防禦分支，靠的就是這個建構子契約。
        assertThatNullPointerException()
                .isThrownBy(() -> new BusinessException(null, "任意訊息"));
    }

    @Test
    @DisplayName("BusinessException 拒絕非 4xx 的 ErrorCode —— 不捕捉 stack trace 的前提只在客戶端錯誤時成立")
    void businessException_ShouldRejectNon4xxErrorCode() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new BusinessException(ErrorCode.INTERNAL_ERROR))
                .withMessageContaining("SystemException");
    }

    @Test
    @DisplayName("處理欄位驗證失敗，應與其他 handler 共用同一個回應格式（code = VALIDATION_FAILED）")
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
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

        // Assert：父類別預設只回一句籠統的 "Invalid request content."，這裡必須換成逐欄位的細節
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = problemOf(responseEntity);
        assertThat(body.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(codeOf(body)).isEqualTo("VALIDATION_FAILED");
        assertThat(body.getTitle()).isEqualTo("參數驗證失敗");
        assertThat(body.getDetail()).isEqualTo("參數驗證失敗: accountId must not be null; items must not be empty");
        // 結構化陣列與 detail 同源，兩者不可能各說各話
        assertThat(errorsOf(body)).containsExactly(
                new ValidationError("accountId", "must not be null"),
                new ValidationError("items", "must not be empty"));
    }

    @Test
    @DisplayName("global（跨欄位）約束的錯誤不得被丟棄，且 field 為 null 以示「不屬於任何欄位」")
    void handleMethodArgumentNotValid_ShouldKeepGlobalErrors() throws Exception {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("methodParameterSource", String.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "createOrderRequest");
        bindingResult.addError(new FieldError("createOrderRequest", "accountId", "must not be null"));
        bindingResult.addError(new ObjectError("createOrderRequest", "min must not exceed max"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleMethodArgumentNotValid(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

        ProblemDetail body = problemOf(responseEntity);
        // 兩種錯誤由 getAllErrors() 一次取得；欄位名靠 FieldError 這個型別本身帶出，不必分兩條路徑收集
        assertThat(errorsOf(body)).containsExactly(
                new ValidationError("accountId", "must not be null"),
                new ValidationError(null, "min must not exceed max"));
        // 沒有欄位名可寫時，detail 只放訊息本身
        assertThat(body.getDetail()).isEqualTo("參數驗證失敗: accountId must not be null; min must not exceed max");
    }

    @Test
    @DisplayName("SystemException 與未預期例外共用同一個 handler，回應皆為不透明的 500")
    void handleUnexpected_ShouldReturnOpaqueInternalServerErrorForBothSources() {
        // Arrange：context 帶了下游細節，正是「只該進 log、不該出現在回應裡」的東西
        SystemException ex = new SystemException("下游 API 呼叫失敗")
                .with("remoteStatus", 502)
                .with("remoteMessage", "Bad Gateway");

        // Act：同一個 handler 吃兩種來源 —— 「刻意宣告的系統錯誤」與「完全沒料到的錯誤」
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleUnexpected(ex, webRequest);
        ResponseEntity<Object> unexpected = globalExceptionHandler.handleUnexpected(
                new IllegalStateException("boom"), webRequest);

        // Assert
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail body = problemOf(responseEntity);
        assertThat(body.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(codeOf(body)).isEqualTo("INTERNAL_ERROR");
        assertThat(body.getTitle()).isEqualTo("伺服器內部錯誤");
        assertThat(body.getDetail()).isEqualTo("系統發生未預期的錯誤，請稍後再試。");

        // context / message 只該進 log，不得洩漏到回應
        assertThat(body.getDetail()).doesNotContain("502", "Bad Gateway", "下游");

        // 兩種來源的對外契約不得分岔（差異只在 log 的資訊量）—— 現在由「共用同一個 handler」保證，
        // 這個斷言只是把該保證釘住，避免日後有人又把它拆成兩個。
        ProblemDetail unexpectedBody = problemOf(unexpected);
        assertThat(responseEntity.getStatusCode()).isEqualTo(unexpected.getStatusCode());
        assertThat(codeOf(body)).isEqualTo(codeOf(unexpectedBody));
        assertThat(body.getTitle()).isEqualTo(unexpectedBody.getTitle());
        assertThat(body.getDetail()).isEqualTo(unexpectedBody.getDetail());
    }

    @Test
    @DisplayName("SystemException 的 context 應保留插入順序，且對外不可變更")
    void systemException_ContextShouldPreserveOrderAndBeUnmodifiable() {
        SystemException ex = new SystemException("下游 API 呼叫失敗")
                .with("remoteStatus", 502)
                .with("remoteMessage", "Bad Gateway");

        // 順序有意義：log 行的可讀性靠它（LinkedHashMap 的 toString 直接就是 log 要的樣子）
        assertThat(ex.getContext()).containsExactly(
                entry("remoteStatus", 502),
                entry("remoteMessage", "Bad Gateway"));

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> ex.getContext().put("injected", "value"));
    }

    /**
     * 取出回應本體。所有 handler 現在都回 {@code ResponseEntity<Object>}（父類別
     * {@code handleExceptionInternal} 的簽章），body 必為 {@link ProblemDetail} —— 這個斷言把它釘住。
     */
    private static ProblemDetail problemOf(ResponseEntity<Object> responseEntity) {
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getBody()).isInstanceOf(ProblemDetail.class);
        return (ProblemDetail) responseEntity.getBody();
    }

    /** {@code errors} 與 {@code code} 同樣是 extension；型別由 handler 保證，測試裡直接轉。 */
    @SuppressWarnings("unchecked")
    private static List<ValidationError> errorsOf(ProblemDetail problem) {
        assertThat(problem.getProperties()).isNotNull();
        return (List<ValidationError>) problem.getProperties().get(GlobalExceptionHandler.ERRORS_PROPERTY);
    }

    /** {@code code} 是 {@link ProblemDetail} 的 extension，存在 properties map 裡而非具名欄位。 */
    private static Object codeOf(ProblemDetail problem) {
        assertThat(problem.getProperties()).isNotNull();
        return problem.getProperties().get(GlobalExceptionHandler.CODE_PROPERTY);
    }

    /** 僅供建構 {@link MethodParameter} 使用 —— MethodArgumentNotValidException 需要一個真實的方法參數。 */
    @SuppressWarnings("unused")
    private void methodParameterSource(String argument) {
        // 刻意留空
    }

}
