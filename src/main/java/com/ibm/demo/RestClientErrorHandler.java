package com.ibm.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.DependencyUnavailableException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;

/**
 * 把跨 domain {@code *Client} 呼叫收到的錯誤回應，翻譯回本地的例外型別。
 *
 * <p>下游其實就是自己（loopback 自呼叫），因此收到的一定是 {@code GlobalExceptionHandler} 產的
 * RFC 9457 {@code application/problem+json}：人類可讀說明在 {@code detail}，機器可讀識別碼在
 * {@code code} extension。
 *
 * <p><b>解析寬鬆，語意嚴格。</b>解析寬鬆：讀 JSON tree 而非綁定型別，多的欄位一律無害，也不依賴
 * {@code ProblemDetailJacksonMixin} 有沒有註冊。語意嚴格：只轉譯「真正 HTTP status 與 {@code code}
 * 的配對在 {@link #TRANSLATABLE} 內」的回應，其餘一律當整合異常（{@link SystemException}，對外 500），
 * <b>不</b>再依 400／404 猜業務原因 —— 遠端 404 可能只是 URL 打錯，被猜成「查無資源」不只對外歸責錯，
 * 還會因為 {@link BusinessException} 被 CircuitBreaker 忽略而讓故障統計漏掉一次真的故障。
 *
 * <p>轉譯結果：
 * <ul>
 *   <li>400／404 的既有業務碼 → {@link BusinessException}，沿用遠端 {@code code}，跨不跨 client 邊界
 *       不改變對外錯誤碼（例如 {@code PRODUCT_STOCK_NOT_ENOUGH} 不會被壓成 {@code INVALID_REQUEST}）。</li>
 *   <li>409 {@code OPTIMISTIC_LOCK_CONFLICT} → {@link BusinessException}，是預期的並發結果。</li>
 *   <li>503 {@code BULKHEAD_FULL}／{@code CIRCUIT_OPEN} → {@link DependencyUnavailableException}，
 *       依賴真的過載，計入故障率。</li>
 *   <li>其餘 → {@link SystemException}。</li>
 * </ul>
 *
 * <p>409／503 的 detail 一律用 {@link ErrorCode#getFixedDetail()} 的本地安全說明，不轉送遠端文字；遠端的 {@code instance}、
 * {@code errors}、headers 也都不轉送。
 */
@Component
public class RestClientErrorHandler {

    /** RFC 9457 中「這一次請求」的人類可讀說明欄位。 */
    private static final String DETAIL_PROPERTY = "detail";

    /** {@code GlobalExceptionHandler} 加的 extension：機器可讀的錯誤碼。 */
    private static final String CODE_PROPERTY = "code";

    /** RFC 9457 的 HTTP status 欄位；可缺席，提供時必須與真正的 HTTP status 一致。 */
    private static final String STATUS_PROPERTY = "status";

    /** 驗證失敗時逐筆列出欄位錯誤的 extension；本地例外承載不了，見 {@link #translate}。 */
    private static final String ERRORS_PROPERTY = "errors";

    /**
     * 允許轉譯的 code。是否接受還要再核對 {@code code} 對應的 status 等於真正的 HTTP status，
     * 因此「HTTP 400 帶 {@code RESOURCE_NOT_FOUND}」不會被接受。
     *
     * <p>這是受控 loopback 的共通政策，不是第三方服務的通用代理：<b>不</b>自動放行所有 4xx enum。
     * 新增可轉譯的 code 必須先審視其對外語意，再加進來並補測試。泛用的 {@code INVALID_REQUEST} 與
     * {@code VALIDATION_FAILED} 刻意不在內 —— 內部 request 可能是呼叫端服務自己組錯，不能直接歸咎外部輸入。
     */
    private static final Set<ErrorCode> TRANSLATABLE = EnumSet.of(
            ErrorCode.ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED,
            ErrorCode.PRODUCT_ALREADY_EXIST,
            ErrorCode.PRODUCT_STILL_HAS_RESERVATION_CAN_NOT_BE_DELETED,
            ErrorCode.PRODUCT_STOCK_NOT_ENOUGH,
            ErrorCode.RESOURCE_NOT_FOUND,
            ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
            ErrorCode.BULKHEAD_FULL,
            ErrorCode.CIRCUIT_OPEN);

    /** 進 context 的 remoteCode 只收長得像 {@code ErrorCode} 常數名的字串，免得不可信的 body 原樣進 log。 */
    private static final Pattern CODE_SHAPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    /** 進 context 的遠端文字最多保留的字元數。 */
    private static final int PREVIEW_LIMIT = 200;

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]+");

    private final ObjectMapper objectMapper;

    public RestClientErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void handle(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        throw translate(status, body);
    }

    /**
     * 依 HTTP status 與 body 決定本地例外。每一道檢查失敗都帶著原因落到 {@link #integrationFailure}，
     * 讓 log 說得出「為什麼沒翻譯」，而不是只剩一個 500。
     */
    private RuntimeException translate(int status, String body) {
        JsonNode problem = parse(body);
        if (problem == null) {
            return integrationFailure(status, null, body, "body 為空或不是可解析的 JSON");
        }
        if (!problem.isObject()) {
            return integrationFailure(status, null, body, "body 不是 JSON object");
        }

        Optional<String> code = text(problem, CODE_PROPERTY);
        String detail = text(problem, DETAIL_PROPERTY).orElse(null);
        if (code.isEmpty()) {
            return integrationFailure(status, null, detail, "缺少 code");
        }
        // errors 存在就不接受（含 errors: null）：那是逐欄位驗證失敗，BusinessException 承載不了陣列，
        // 而且內部 request 的欄位不保證對得上外部欄位。
        if (!problem.path(ERRORS_PROPERTY).isMissingNode()) {
            return integrationFailure(status, code.get(), detail, "帶有 errors 屬性（遠端驗證失敗）");
        }
        if (!statusMatches(problem, status)) {
            return integrationFailure(status, code.get(), detail, "body.status 與 HTTP status 不一致或型別錯誤");
        }

        Optional<ErrorCode> resolved = resolve(code.get())
                .filter(TRANSLATABLE::contains)
                .filter(candidate -> candidate.getStatus().value() == status);
        if (resolved.isEmpty()) {
            return integrationFailure(status, code.get(), detail, "此 status／code 配對不在可轉譯清單內");
        }

        ErrorCode errorCode = resolved.get();
        // 有固定說明的（409／503）一律用本地的，不轉送遠端文字；其餘是已審視可公開的業務說明，
        // 缺少或空白時退回本地 title。
        String fixedDetail = errorCode.getFixedDetail();
        if (errorCode == ErrorCode.BULKHEAD_FULL || errorCode == ErrorCode.CIRCUIT_OPEN) {
            return new DependencyUnavailableException(errorCode, fixedDetail);
        }
        return new BusinessException(errorCode,
                fixedDetail != null ? fixedDetail : Optional.ofNullable(detail).orElse(errorCode.getTitle()));
    }

    /**
     * 整合異常：遠端回了我們無法確認語意的東西。對外是不透明的 500，排查資訊只進 context。
     *
     * <p>context 放：狀態、長得像 code 的字串、分類失敗原因，以及 {@code remoteDetail} —— 遠端的說明文字
     * （或無法解析時 body 的開頭），經 {@link #preview} 去掉換行與控制字元並截短。這是不可信輸入，所以
     * <b>只進 context、不進回應</b>，且不會把完整 body 寫進 log；沒有它，值班的人面對「HTML 的 502」或
     * 「空 body」時連對方回了什麼都看不到。
     */
    private SystemException integrationFailure(
            int status, @Nullable String remoteCode, @Nullable String remoteText, String reason) {
        SystemException ex = new SystemException("下游 API 呼叫失敗")
                .with("remoteStatus", status)
                .with("reason", reason);
        if (remoteCode != null && CODE_SHAPE.matcher(remoteCode).matches()) {
            ex.with("remoteCode", remoteCode);
        }
        String preview = preview(remoteText);
        if (!preview.isEmpty()) {
            ex.with("remoteDetail", preview);
        }
        return ex;
    }

    /** 把不可信文字壓成單行並截短：控制字元（含換行）一律換成空白，避免偽造 log 行。 */
    static String preview(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String oneLine = CONTROL_CHARS.matcher(text).replaceAll(" ").strip();
        return oneLine.length() <= PREVIEW_LIMIT ? oneLine : oneLine.substring(0, PREVIEW_LIMIT) + "…";
    }

    /** body.status 可缺席；提供時必須是等於真正 HTTP status 的整數。 */
    private static boolean statusMatches(JsonNode problem, int httpStatus) {
        JsonNode bodyStatus = problem.path(STATUS_PROPERTY);
        return bodyStatus.isMissingNode() || (bodyStatus.isIntegralNumber() && bodyStatus.intValue() == httpStatus);
    }

    /** {@code valueOf} 對認不出的 code 拋例外，而「認不出」在這裡是預期情況，故收成空值。 */
    private static Optional<ErrorCode> resolve(String code) {
        try {
            return Optional.of(ErrorCode.valueOf(code));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 把 tree 上一個字串欄位取成 {@link Optional}，空白值視同缺席。 */
    private static Optional<String> text(JsonNode problem, String property) {
        return problem.path(property).stringValueOpt().filter(value -> !value.isBlank());
    }

    /** 空 body 或解析失敗回 {@code null}；解析失敗本身不記 log，body 開頭由 {@link #integrationFailure} 截短後放進 context。 */
    private @Nullable JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }
}
