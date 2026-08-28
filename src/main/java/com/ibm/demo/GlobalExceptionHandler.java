package com.ibm.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
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
import lombok.extern.slf4j.Slf4j;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;
import com.ibm.demo.exception.ValidationError;

/**
 * 全域例外處理，同時是**唯一**記錄例外的地方。
 *
 * <p>繼承 {@code ResponseEntityExceptionHandler} 的用意就是它 javadoc 寫的那件事：Spring MVC 自己拋的
 * 那批例外（405、415、malformed JSON…）它已經處理好，回 RFC 9457 的 {@code application/problem+json}
 * （{@link ProblemDetail}）。我們只補三件事：
 * <ol>
 *   <li>覆寫兩個驗證相關的 {@code handleXxx} —— 父類別預設的 detail 沒說哪個欄位錯了；</li>
 *   <li>為自己的例外（{@link BusinessException}、resilience4j、Security…）加 {@code @ExceptionHandler}；</li>
 *   <li>覆寫 {@link #handleExceptionInternal} 做「所有例外的共同處理」—— 補 {@code code}／{@code type}、記 log。</li>
 * </ol>
 *
 * <p><b>自訂 handler 也把 body 交給 {@link #handleExceptionInternal}，不自己 {@code new ResponseEntity}。</b>
 * 於是「框架攔到的」與「我們攔到的」共用同一段收尾（含父類別對「回應已 commit」的判斷，以及最後那步
 * {@code createResponseEntity}），格式不會因為「例外由誰攔到」這種呼叫端無法預測的內部細節而分岔。
 *
 * <p>body 用框架的 {@link ErrorResponse#builder} 組，理由僅僅是它比 {@code new ProblemDetail} 再加四行
 * setter 短。
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
 *   <li><b>未預期</b>（500）→ ERROR 並帶完整 stack trace。分兩種來源：{@link SystemException} 是
 *       throw 點**刻意宣告**「這裡壞了」（因此帶得動排查用 context），其餘則是真的沒人料到。
 *       兩者對外回應完全相同 —— 這點現在由「共用同一個 handler」保證，而非靠兩個 handler 各自
 *       維持一致；差別只在 log 有沒有 context。</li>
 * </ul>
 *
 * <p>記錄等級因此由**最終 HTTP status** 決定、而非各 handler 各自判斷：500 進 ERROR，其餘進 WARN。
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
    public @Nullable ResponseEntity<Object> handleBusinessException(BusinessException ex, WebRequest request) {
        // errorCode 由 BusinessException 建構子保證非 null 且必為 4xx，此處無須防禦性分支。
        return respond(ex, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(BulkheadFullException.class)
    public @Nullable ResponseEntity<Object> handleBulkheadFull(BulkheadFullException ex, WebRequest request) {
        return respond(ex, ErrorCode.BULKHEAD_FULL, "系統負載過高，請稍後再試。", request);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public @Nullable ResponseEntity<Object> handleCallNotPermitted(CallNotPermittedException ex, WebRequest request) {
        return respond(ex, ErrorCode.CIRCUIT_OPEN, "服務暫時不可用，請稍後再試。", request);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public @Nullable ResponseEntity<Object> handleRateLimiter(RequestNotPermitted ex, WebRequest request) {
        return respond(ex, ErrorCode.RATE_LIMITED, "請求過於頻繁，請稍後再試。", request);
    }

    // 處理樂觀鎖衝突例外
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public @Nullable ResponseEntity<Object> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex, WebRequest request) {
        return respond(ex, ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "資料已被其他使用者修改，請重新整理後再試。", request);
    }

    /**
     * 認證失敗（401）。由 {@code SecurityConfig} 的 authenticationEntryPoint 委派過來 —— filter chain
     * 在 DispatcherServlet 之前，@RestControllerAdvice 本身接不到它。
     *
     * <p>{@code detail} 固定、不帶 {@code ex.getMessage()}：Spring Security 的訊息會區分「找不到使用者」
     * 與「密碼錯誤」，回給呼叫端等於送出帳號枚舉的線索。要排查看 log 就好。
     */
    @ExceptionHandler(AuthenticationException.class)
    public @Nullable ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return respond(ex, ErrorCode.UNAUTHORIZED, "需要有效的認證憑證。", request);
    }

    /**
     * 授權失敗（403）。兩個來源：Security filter chain 委派過來的，以及 controller/service 內直接拋的。
     *
     * <p>後者原本會被 {@link #handleUnexpected} 吃成 500 —— 也就是說一旦導入方法級授權
     * （{@code @PreAuthorize} 拋的 {@code AuthorizationDeniedException} 是本型別的子類別），
     * 「沒權限」會對外表現成「伺服器壞了」。這個 handler 必須排在 catch-all 之前才有意義。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public @Nullable ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return respond(ex, ErrorCode.FORBIDDEN, "沒有權限執行此操作。", request);
    }

    /**
     * 500：真的壞了、需要有人去看。
     *
     * <p>{@link SystemException}（throw 點刻意宣告「這裡壞了」）與完全沒料到的例外**共用這一個
     * handler**，因為它們的對外回應必須一模一樣 —— 訊息不回給呼叫端（避免洩漏內部細節）、
     * {@code detail} 固定、{@code code} 一律 {@code INTERNAL_ERROR}。分成兩個 handler 只會讓
     * 「兩者不得分岔」變成一條要靠測試看守的約定；共用一個則是結構上不可能分岔。
     *
     * <p>差別全在 log，由 {@link #log} 依例外型別決定：{@code SystemException} 多印它帶的 context
     * （哪個下游、回了什麼），不必靠 stack trace 反推。
     *
     * <p>注意：{@code ResponseEntityExceptionHandler} 內建處理的那批 Spring MVC 例外仍會優先命中
     * （Spring 選最具體的 handler），不會被這個 catch-all 攔走。認證／授權失敗同理，由上面兩個
     * 明確的 handler 接走 —— 新增 handler 時請一併確認它排在這個 catch-all 之前。
     */
    @ExceptionHandler(Exception.class)
    public @Nullable ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return respond(ex, ErrorCode.INTERNAL_ERROR, "系統發生未預期的錯誤，請稍後再試。", request);
    }

    /**
     * {@code @Valid @RequestBody} 的欄位驗證失敗。父類別預設只回一句
     * {@code "Invalid request content."}，呼叫端得不到「哪個欄位錯了」，因此必須 override。
     *
     * <p>{@code getAllErrors()} 一次拿到 field error 與 global（class-level）error —— 後者曾被靜默
     * 丟棄，也就是「起始日不得晚於結束日」這類跨欄位約束擋下請求後，呼叫端拿到的是一個沒有任何
     * 原因的 400。欄位名由 {@link FieldError} 這個型別本身帶出，不必另外分兩條路徑收集。
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationError> errors = new ArrayList<>();
        collect(ex.getBindingResult().getAllErrors(), null, errors);
        return validationFailed(ex, errors, headers, request);
    }

    /**
     * 方法參數層級的驗證失敗：{@code @RequestParam}／{@code @PathVariable} 上的約束
     * （如 {@code @Positive int page}）由 Spring 6.1 起內建的 method validation 檢查，拋的是本例外
     * 而非 {@link MethodArgumentNotValidException}。
     *
     * <p>父類別對它只回一句籠統的 detail。沒有這個 override，同一件事（參數不合法）會因為約束寫在
     * request body 還是 query param 上而回出兩種形狀 —— 而呼叫端在意的是「哪個參數錯了」，不是
     * Spring 用哪個機制檢查的。
     *
     * <p>「參數本身是個 bean」與「純量參數」的分流交給框架的
     * {@code getBeanResults()}／{@code getValueResults()}，不必自己 {@code instanceof}：前者的錯誤
     * 是 {@link FieldError}（欄位名在錯誤裡），後者的欄位名則是參數名。
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationError> errors = new ArrayList<>();
        // 參數是個 bean（@Valid 的巢狀物件）：欄位名由 FieldError 帶出
        ex.getBeanResults().forEach(result -> collect(result.getAllErrors(), null, errors));
        // 純量參數：約束直接掛在參數上，欄位名就是參數名
        ex.getValueResults().forEach(result ->
                collect(result.getResolvableErrors(), result.getMethodParameter().getParameterName(), errors));
        // 跨參數約束（如「兩個參數不得同時為空」）沒有對應欄位
        collect(ex.getCrossParameterValidationResults(), null, errors);

        return validationFailed(ex, errors, headers, request);
    }

    /**
     * 驗證失敗的共用回應：兩條驗證路徑（request body / 方法參數）在此合流，格式因此不可能分岔。
     *
     * <p>{@code detail} 與 {@code errors} 都由同一個 list 產出：前者給人看（也是本專案跨 domain 呼叫時
     * {@code RestClientErrorHandler} 唯一取用的欄位，因此不能只寫「有 3 個欄位錯誤」這種沒資訊量的話），
     * 後者給程式看。
     */
    private @Nullable ResponseEntity<Object> validationFailed(
            Exception ex, List<ValidationError> errors, HttpHeaders headers, WebRequest request) {

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        String detail = errors.isEmpty()
                ? errorCode.getTitle() + "。"
                : errorCode.getTitle() + ": " + errors.stream()
                        .map(ValidationError::describe)
                        .collect(Collectors.joining("; "));

        ProblemDetail body = problem(ex, errorCode, detail);
        body.setProperty(ERRORS_PROPERTY, errors);
        // 父類別規定這兩個 override 回傳 ResponseEntity<Object>，但收尾仍交給同一個 hook，
        // 因此 headers 不會在這條路徑上被吃掉（415 的 Accept、405 的 Allow 都靠它帶出去）。
        return handleExceptionInternal(ex, body, headers, errorCode.getStatus(), request);
    }

    /**
     * 把一批 Bean Validation 錯誤收成 {@link ValidationError}。
     *
     * <p>參數型別取 {@link MessageSourceResolvable} 而非 {@code ObjectError}，因為兩條驗證路徑收到的
     * 錯誤只有這個共同父型別（純量參數的錯誤不是 {@code ObjectError}）。訊息直接讀
     * {@code getDefaultMessage()} —— Bean Validation 已經把 {@code {min}} 之類的佔位符插補好了。
     *
     * @param fallbackField 錯誤本身沒帶欄位名時要用的欄位名（純量參數的參數名）；null 表示
     *        「這批錯誤不屬於任何單一欄位」，交由 {@link ValidationError} 以缺席欄位表示
     */
    private void collect(List<? extends MessageSourceResolvable> resolvables,
            @Nullable String fallbackField, List<ValidationError> target) {

        for (MessageSourceResolvable resolvable : resolvables) {
            String field = (resolvable instanceof FieldError fieldError) ? fieldError.getField() : fallbackField;
            target.add(new ValidationError(field, resolvable.getDefaultMessage()));
        }
    }

    /**
     * <b>所有</b>錯誤回應的單一漏斗 —— 父類別內建處理的那批 Spring MVC 例外（405、415、malformed
     * JSON、缺參數…）與本類別自訂的 handler 最後都經過這裡。
     *
     * <p>攔在這一層做三件事：
     * <ul>
     *   <li>補上 {@code code} 與 {@code type}。父類別產的 {@code ProblemDetail} 只有
     *       {@code about:blank} 型別（Spring 不序列化預設值，實際線路上是整個 {@code type} 欄位缺席），
     *       呼叫端拿不到任何機器可讀識別碼，只能對 HTTP status 分流 —— 而多個不同原因共用 400。</li>
     *   <li>記一行 log。協定層錯誤原本完全靜默，線上收到 415 時 log 裡查無此事。</li>
     *   <li>保留父類別的收尾行為：回應已 commit 時回 {@code null} 而不是硬寫第二份 body。自訂 handler
     *       過去自己 {@code new ResponseEntity} 就跳過了這個判斷。</li>
     * </ul>
     *
     * <p>選這個 hook 而非 {@code createResponseEntity}，是因為只有這裡同時看得到**例外**與**最終
     * status** —— 記錄等級與 stack trace 該不該印，靠的正是這兩個資訊。
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        // ErrorResponse 例外（父類別內建那批的多數）自己帶 body，父類別會在下面幾行後取出。提前取一次
        // 才能在記錄與補欄位時看到「真正要寫出去的內容」；取完 body 非 null，父類別那段就會跳過。
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }

        ProblemDetail problem = (body instanceof ProblemDetail detail) ? detail : null;
        log(ex, complete(problem, statusCode), problem, statusCode, request);

        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /**
     * 補齊 {@code code} 與 {@code type}，回傳這次錯誤的 code。
     *
     * <p>自訂 handler 產的 body 已經帶了 {@link ErrorCode} 的 code，沿用；父類別產的沒有，改由 HTTP
     * 狀態名推導（405 → {@code METHOD_NOT_ALLOWED}）。這批協定層錯誤沒有對應的 {@link ErrorCode}
     * ——它們是協定層而非業務層 —— 但 {@code type} 仍由 {@link ErrorCode#typeOf} 推導，規則與其餘錯誤
     * 同源，{@code title} 則沿用父類別給的 HTTP reason phrase（英文）：這類錯誤的讀者是開發者。
     */
    private String complete(@Nullable ProblemDetail problem, HttpStatusCode statusCode) {
        String code = existingCode(problem);
        if (code == null) {
            code = frameworkCode(statusCode);
        }
        if (problem != null) {
            problem.setProperty(CODE_PROPERTY, code);
            // 只在 type 缺值時補，保持 idempotent。
            if (problem.getType() == null || DEFAULT_TYPE.equals(problem.getType().toString())) {
                problem.setType(ErrorCode.typeOf(code));
            }
        }
        return code;
    }

    private @Nullable String existingCode(@Nullable ProblemDetail problem) {
        Map<String, Object> properties = (problem != null) ? problem.getProperties() : null;
        return (properties != null && properties.get(CODE_PROPERTY) instanceof String code) ? code : null;
    }

    /** 協定層錯誤的 code：HTTP 狀態名。無法解析的狀態碼退回 {@code HTTP_<數字>}，保證欄位永遠有值。 */
    private String frameworkCode(HttpStatusCode statusCode) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        return resolved != null ? resolved.name() : "HTTP_" + statusCode.value();
    }

    /**
     * 組回應 —— 自訂 handler 的共用出口：組 body、交給 {@link #handleExceptionInternal} 收尾。
     *
     * <p>{@code code} 同時扮演兩個角色：log 行的前綴，以及回應的 {@code code} 欄位 —— 刻意共用同一個
     * 值，客戶端回報的錯誤碼才能直接拿去 grep log。
     */
    private @Nullable ResponseEntity<Object> respond(
            Exception ex, ErrorCode errorCode, @Nullable String detail, WebRequest request) {

        return handleExceptionInternal(
                ex, problem(ex, errorCode, detail), new HttpHeaders(), errorCode.getStatus(), request);
    }

    /**
     * 組裝回應本體的**唯一**入口 —— 所有自訂 handler（含兩個驗證用的父類別 override）都經由這裡，
     * 格式才不會分岔。
     *
     * <p>用框架的 {@link ErrorResponse#builder} 而非手刻 {@code ProblemDetail}：少寫四行 setter，沒有
     * 別的用意。builder 上那組 {@code *MessageCode()} 方法（把三個文字欄位搬到
     * {@code messages.properties}）刻意不接 —— 本專案沒有多語系需求，而一旦放了 bundle，回應內容就會
     * 隨 {@code Accept-Language} 而變，那是 API 契約變更。
     *
     * <p>{@code instance} 不在此設定：交給 Spring 的回傳值處理器自動填入 request URI，少一處可能漏填。
     */
    private ProblemDetail problem(Exception ex, ErrorCode errorCode, @Nullable String detail) {
        return ErrorResponse.builder(ex, errorCode.getStatus(), detail != null ? detail : errorCode.getTitle())
                .type(errorCode.getType())
                .title(errorCode.getTitle())
                .property(CODE_PROPERTY, errorCode.getCode())
                .build()
                .getBody();
    }

    /**
     * 唯一的記錄點。等級由**最終 HTTP status** 決定，而非各 handler 自己想一次：500 是「沒人料到、
     * 要有人去看」，其餘（含 resilience4j 的 503/429）是預期結果，一行就夠。
     *
     * <p>500 的那行刻意印出例外類別名：它取代了舊版 {@code [SYSTEM_ERROR]}／{@code [UNEXPECTED]}
     * 兩個前綴，而且資訊更多 —— 前綴位置現在讓給對外回的 {@code code}，客戶端回報的錯誤碼才能直接
     * 拿去 grep。
     */
    private void log(Exception ex, String code, @Nullable ProblemDetail problem,
            HttpStatusCode statusCode, WebRequest request) {

        String method = "-";
        String uri = request.getDescription(false);
        if (request instanceof ServletWebRequest servletWebRequest) {
            method = servletWebRequest.getRequest().getMethod();
            uri = servletWebRequest.getRequest().getRequestURI();
        }

        if (statusCode.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            // throw 點刻意附上的 context（哪個下游、回了什麼）不必靠 stack trace 反推。
            Object context = (ex instanceof SystemException systemEx) ? systemEx.getContext() : Map.of();
            // 佔位符 6 個、參數 7 個 → SLF4J 把最後的 ex 當 Throwable 處理，印出完整 stack trace。
            log.error("[{}] 500 {} {} - {}: {} {}", code, method, uri,
                    ex.getClass().getSimpleName(), ex.getMessage(), context, ex);
        } else {
            // 這類例外的資訊量全在 status + code + 請求路徑 + detail 裡；stack trace 只會把真正的錯誤淹掉。
            String detail = (problem != null) ? problem.getDetail() : ex.getMessage();
            log.warn("[{}] {} {} {} - {}", code, statusCode.value(), method, uri, detail);
        }
    }
}
