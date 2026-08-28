package com.ibm.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;
import com.ibm.demo.exception.ValidationError;

/**
 * 全域例外處理，同時是**唯一**記錄例外的地方。
 *
 * <p>對外一律回 RFC 9457 的 {@code application/problem+json}（{@link ProblemDetail}）。這不是
 * 「跟隨標準」的姿態問題 —— 而是這個 class 的父類別 {@code ResponseEntityExceptionHandler} 對
 * 它內建處理的那批 Spring MVC 例外（405、415、malformed JSON…）本來就只會產 {@code ProblemDetail}。
 * 自訂 handler 若另立一套 JSON 欄位，同一支 API 就有兩種錯誤格式，而且分岔點是「例外由誰攔到」
 * 這種呼叫端完全無法預測的內部細節。收斂到 {@code ProblemDetail} 是唯一能讓兩條路徑合流的方向。
 *
 * <p>標準欄位之外只加一個 extension：{@code code}。{@code type} 雖然也是機器可讀識別碼，但要求
 * 呼叫端剖 URI 才能 switch 並不友善；{@code code} 由 {@link ErrorCode#getCode()} 提供，而
 * {@code type} 由同一個 code 機械推導（{@link ErrorCode#typeOf}），兩者結構上不可能不一致。
 *
 * <p>{@code instance}（出錯的請求路徑）不必手動塞：Spring 的回傳值處理器在 body 是
 * {@code ProblemDetail} 且 {@code instance} 為 null 時，會自動補上 request URI。
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
 *   <li><b>未預期</b>→ ERROR 並帶完整 stack trace。分兩種來源：
 *       {@link SystemException} 是 throw 點**刻意宣告**「這裡壞了」（因此帶得動排查用 context），
 *       {@link #handleUnexpected} 則是真的沒人料到。兩者對外回應完全相同，差別只在 log 的資訊量。</li>
 * </ul>
 *
 * <p>記錄等級因此是**由例外型別決定**、而非各 handler 各自判斷：BusinessException 進 WARN、
 * SystemException 進 ERROR，不需要誰在 handler 裡再想一次。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** {@code code} extension 的欄位名，供契約測試引用，避免測試裡散落字面值。 */
    public static final String CODE_PROPERTY = "code";

    /** 驗證失敗時逐筆列出欄位錯誤的 extension 欄位名。 */
    public static final String ERRORS_PROPERTY = "errors";

    /** {@code ProblemDetail} 未指定 type 時的預設值；Spring 不會序列化它，等同「沒有 type」。 */
    private static final String DEFAULT_TYPE = "about:blank";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        // errorCode 由 BusinessException 建構子保證非 null 且必為 4xx，此處無須防禦性分支。
        return respond(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ProblemDetail> handleBulkheadFull(BulkheadFullException ex, HttpServletRequest request) {
        return respond(ErrorCode.BULKHEAD_FULL, "系統負載過高，請稍後再試。", request);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCallNotPermitted(CallNotPermittedException ex,
            HttpServletRequest request) {
        return respond(ErrorCode.CIRCUIT_OPEN, "服務暫時不可用，請稍後再試。", request);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ProblemDetail> handleRateLimiter(RequestNotPermitted ex, HttpServletRequest request) {
        return respond(ErrorCode.RATE_LIMITED, "請求過於頻繁，請稍後再試。", request);
    }

    // 處理樂觀鎖衝突例外
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        return respond(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "資料已被其他使用者修改，請重新整理後再試。", request);
    }

    /**
     * 系統／整合失敗：與 {@link #handleUnexpected} 回一模一樣的 500 回應，差別全在 log ——
     * 這裡多了 throw 點刻意附上的 {@code context}（哪個下游、回了什麼），不必靠 stack trace 反推。
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ProblemDetail> handleSystemException(SystemException ex, HttpServletRequest request) {
        // 佔位符 4 個、參數 5 個 → SLF4J 把最後的 ex 當 Throwable 處理，印出完整 stack trace。
        log.error("[SYSTEM_ERROR] 500 {} {} - {} {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex.getContext(), ex);
        return internalServerError();
    }

    /**
     * 認證失敗（401）。由 {@code SecurityConfig} 的 authenticationEntryPoint 委派過來 —— filter chain
     * 在 DispatcherServlet 之前，@RestControllerAdvice 本身接不到它。
     *
     * <p>{@code detail} 固定、不帶 {@code ex.getMessage()}：Spring Security 的訊息會區分「找不到使用者」
     * 與「密碼錯誤」，回給呼叫端等於送出帳號枚舉的線索。要排查看 log 就好。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex,
            HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHORIZED, "需要有效的認證憑證。", request);
    }

    /**
     * 授權失敗（403）。兩個來源：Security filter chain 委派過來的，以及 controller/service 內直接拋的。
     *
     * <p>後者原本會被 {@link #handleUnexpected} 吃成 500 —— 也就是說一旦導入方法級授權
     * （{@code @PreAuthorize} 拋的 {@code AuthorizationDeniedException} 是本型別的子類別），
     * 「沒權限」會對外表現成「伺服器壞了」。這個 handler 必須排在 catch-all 之前才有意義。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, "沒有權限執行此操作。", request);
    }

    /**
     * 未預期的例外：真的壞了、需要有人去看，因此這是**唯一**該印 stack trace 的地方。
     *
     * <p>訊息不回給呼叫端（避免洩漏內部細節），只回統一的 problem+json 格式。
     *
     * <p>注意：{@code ResponseEntityExceptionHandler} 內建處理的那批 Spring MVC 例外仍會優先命中
     * （Spring 選最具體的 handler），不會被這個 catch-all 攔走。認證／授權失敗同理，由上面兩個
     * 明確的 handler 接走 —— 新增 handler 時請一併確認它排在這個 catch-all 之前。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 最後一個參數是 Throwable → SLF4J 會印出完整 stack trace。
        log.error("[UNEXPECTED] 500 {} {}", request.getMethod(), request.getRequestURI(), ex);
        return internalServerError();
    }

    /**
     * {@code @Valid @RequestBody} 的欄位驗證失敗。父類別預設只回一句
     * {@code "Invalid request content."}，呼叫端得不到「哪個欄位錯了」，因此必須 override。
     *
     * <p>field error 與 global（class-level）error 都收 —— 後者曾被靜默丟棄，也就是「起始日不得晚於
     * 結束日」這類跨欄位約束擋下請求後，呼叫端拿到的是一個沒有任何原因的 400。
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.add(new ValidationError(error.getField(), error.getDefaultMessage())));
        // global error 沒有對應欄位（跨欄位約束），field 留 null 由序列化略去
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(new ValidationError(null, error.getDefaultMessage())));

        return validationFailed(errors, headers, request);
    }

    /**
     * 方法參數層級的驗證失敗：{@code @RequestParam}／{@code @PathVariable} 上的約束
     * （如 {@code @Positive int page}）由 Spring 6.1 起內建的 method validation 檢查，拋的是本例外
     * 而非 {@link MethodArgumentNotValidException}。
     *
     * <p>父類別對它只回一句籠統的 detail。沒有這個 override，同一件事（參數不合法）會因為約束寫在
     * request body 還是 query param 上而回出兩種形狀 —— 而呼叫端在意的是「哪個參數錯了」，不是
     * Spring 用哪個機制檢查的。
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationError> errors = new ArrayList<>();
        for (var result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                // 參數本身是個 bean（@Valid 的巢狀物件）：錯誤帶得出欄位名
                parameterErrors.getFieldErrors()
                        .forEach(error -> errors.add(new ValidationError(error.getField(), error.getDefaultMessage())));
                parameterErrors.getGlobalErrors()
                        .forEach(error -> errors.add(new ValidationError(null, error.getDefaultMessage())));
            } else {
                // 純量參數：約束直接掛在參數上，欄位名就是參數名
                String field = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(error -> errors.add(new ValidationError(field, error.getDefaultMessage())));
            }
        }
        // 跨參數約束（如「兩個參數不得同時為空」）同樣沒有對應欄位
        ex.getCrossParameterValidationResults()
                .forEach(error -> errors.add(new ValidationError(null, error.getDefaultMessage())));

        return validationFailed(errors, headers, request);
    }

    /**
     * 驗證失敗的共用回應：兩條驗證路徑（request body / 方法參數）在此合流，格式因此不可能分岔。
     *
     * <p>{@code detail} 與 {@code errors} 都由同一個 list 產出：前者給人看（也是本專案跨 domain 呼叫時
     * {@code RestClientErrorHandler} 唯一取用的欄位，因此不能只寫「有 3 個欄位錯誤」這種沒資訊量的話），
     * 後者給程式看。
     */
    private ResponseEntity<Object> validationFailed(
            List<ValidationError> errors, HttpHeaders headers, WebRequest request) {

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        String detail = errors.isEmpty()
                ? errorCode.getTitle() + "。"
                : errorCode.getTitle() + ": " + errors.stream()
                        .map(ValidationError::describe)
                        .collect(Collectors.joining("; "));

        // 這兩個 override 的回傳型別是父類別規定的 ResponseEntity<Object>，無法直接借用 respond()，
        // 但回應本體一律由 problem() 組裝 —— 格式與其他 handler 保證一致。
        if (request instanceof ServletWebRequest servletWebRequest) {
            logExpected(errorCode, detail, servletWebRequest.getRequest());
        }

        ProblemDetail problem = problem(errorCode, detail);
        problem.setProperty(ERRORS_PROPERTY, errors);
        return new ResponseEntity<>(problem, headers, errorCode.getStatus());
    }

    /**
     * 父類別內建處理的那批 Spring MVC 例外（405、415、malformed JSON、缺參數…）最後都經過這裡。
     *
     * <p>攔在這一層做兩件事，讓「框架攔到的」與「我們攔到的」在契約上齊平：
     * <ul>
     *   <li>補上 {@code code} 與 {@code type}。父類別產的 {@code ProblemDetail} 只有
     *       {@code about:blank} 型別（Spring 不序列化預設值，實際線路上是整個 {@code type} 欄位缺席），
     *       呼叫端拿不到任何機器可讀識別碼，只能對 HTTP status 分流 —— 而多個不同原因共用 400。</li>
     *   <li>記一行 WARN。這些協定層錯誤原本完全靜默，線上收到 415 時 log 裡查無此事。</li>
     * </ul>
     *
     * <p>這批錯誤沒有對應的 {@link ErrorCode}（它們是協定層而非業務層），因此 code 取 HTTP 狀態名
     * （405 → {@code METHOD_NOT_ALLOWED}），type 仍由 {@link ErrorCode#typeOf} 推導，規則與其餘錯誤同源。
     * {@code title} 沿用父類別給的 HTTP reason phrase（英文）—— 這類錯誤的讀者是開發者不是終端使用者。
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        if (body instanceof ProblemDetail problem) {
            String code = frameworkCode(statusCode);
            // 只在 type 缺值時補：自訂 handler 不會走到這個 hook，但保持 idempotent 才不怕日後改動。
            if (problem.getType() == null || DEFAULT_TYPE.equals(problem.getType().toString())) {
                problem.setType(ErrorCode.typeOf(code));
            }
            problem.setProperty(CODE_PROPERTY, code);
            if (request instanceof ServletWebRequest servletWebRequest) {
                log.warn("[{}] {} {} {} - {}", code, statusCode.value(),
                        servletWebRequest.getRequest().getMethod(),
                        servletWebRequest.getRequest().getRequestURI(), problem.getDetail());
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** 協定層錯誤的 code：HTTP 狀態名。無法解析的狀態碼退回 {@code HTTP_<數字>}，保證欄位永遠有值。 */
    private String frameworkCode(HttpStatusCode statusCode) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        return resolved != null ? resolved.name() : "HTTP_" + statusCode.value();
    }

    /**
     * 500 的回應本體：{@code detail} 固定、不含任何內部細節 —— 呼叫端無法對成因分流，透露實作只是
     * 給攻擊者情報。
     *
     * <p>{@link #handleSystemException} 與 {@link #handleUnexpected} 共用它，避免「刻意宣告的系統錯誤」
     * 與「沒料到的錯誤」在對外契約上分岔（那個差異只該出現在 log 裡）。
     */
    private ResponseEntity<ProblemDetail> internalServerError() {
        return new ResponseEntity<>(
                problem(ErrorCode.INTERNAL_ERROR, "系統發生未預期的錯誤，請稍後再試。"),
                ErrorCode.INTERNAL_ERROR.getStatus());
    }

    /**
     * 預期的例外：記一行、不帶 stack trace，然後組回應。
     *
     * <p>這類例外的資訊量全在 status + code + 請求路徑 + detail 裡；stack trace 只會把真正的錯誤淹掉。
     *
     * <p>{@code code} 同時扮演兩個角色：log 行的前綴，以及回應的 {@code code} 欄位 —— 刻意共用同一個
     * 值，客戶端回報的錯誤碼才能直接拿去 grep log。
     */
    private ResponseEntity<ProblemDetail> respond(ErrorCode errorCode, String detail, HttpServletRequest request) {
        logExpected(errorCode, detail, request);
        return new ResponseEntity<>(problem(errorCode, detail), errorCode.getStatus());
    }

    private void logExpected(ErrorCode errorCode, String detail, HttpServletRequest request) {
        log.warn("[{}] {} {} {} - {}", errorCode.getCode(), errorCode.getStatus().value(),
                request.getMethod(), request.getRequestURI(), detail);
    }

    /**
     * 組裝回應本體的**唯一**入口 —— 所有自訂 handler（含 {@code handleMethodArgumentNotValid} 這個
     * 父類別 override）都經由這裡，格式才不會分岔。
     *
     * <p>{@code instance} 不在此設定：交給 Spring 的回傳值處理器自動填入 request URI，少一處可能漏填。
     */
    private ProblemDetail problem(ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detail);
        problem.setType(errorCode.getType());
        problem.setTitle(errorCode.getTitle());
        problem.setProperty(CODE_PROPERTY, errorCode.getCode());
        return problem;
    }
}
