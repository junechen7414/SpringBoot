package com.ibm.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;

/**
 * 把跨 domain {@code *Client} 呼叫收到的錯誤回應，翻譯回本地的例外型別。
 *
 * <p>下游其實就是自己（loopback 自呼叫），因此收到的一定是 {@code GlobalExceptionHandler} 產的
 * RFC 9457 {@code application/problem+json}：人類可讀說明在 {@code detail}，機器可讀識別碼在
 * {@code code} extension。
 *
 * <p><b>兩個欄位都要沿用，否則業務碼會在這一跳消失。</b>原本只讀 {@code detail}、{@code ErrorCode}
 * 純粹由 HTTP status 推導，於是 product domain 拋的 {@code PRODUCT_STOCK_NOT_ENOUGH} 經
 * {@code ProductClient} 回到 order domain 後被重建成 {@code INVALID_REQUEST} —— 呼叫端在
 * {@code POST /order} 上只看得到「400 + INVALID_REQUEST」，且它同時代表「訂單內重複商品」
 * （order 本地拋的真正 {@code INVALID_REQUEST}）與「庫存不足」兩件事，只能靠 {@code detail}
 * 字串分辨。沿用下游的 {@code code} 後，跨不跨 client 邊界不再改變對外的錯誤碼。
 *
 * <p><b>刻意讀 JSON tree 而非反序列化成型別</b>：綁定到 {@code ProblemDetail} 會連帶依賴
 * {@code ProblemDetailJacksonMixin} 有沒有註冊（沒註冊時 {@code code} 這個 extension 欄位會被當成
 * 未知屬性而整包解析失敗），綁定到自訂 record 則多一個必須跟著契約同步的型別。讀 tree 只認欄位名，
 * 多的欄位一律無害。
 */
@Slf4j
@Component
public class RestClientErrorHandler {

    /** RFC 9457 中「這一次請求」的人類可讀說明欄位。 */
    private static final String DETAIL_PROPERTY = "detail";

    /** {@code GlobalExceptionHandler} 加的 extension：機器可讀的錯誤碼。 */
    private static final String CODE_PROPERTY = "code";

    /** 驗證失敗時逐筆列出欄位錯誤的 extension，本地例外承載不了 —— 見 {@link #remoteErrorCode}。 */
    private static final String ERRORS_PROPERTY = "errors";

    private final ObjectMapper objectMapper;

    public RestClientErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void handle(ClientHttpResponse response) throws IOException {
        // 讀取 Body 內容 (確保只讀取一次並轉為 String)
        String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        // detail 與 code 出自同一棵 tree，因此只 parse 一次、解析失敗也只記一行 WARN。
        JsonNode problem = parse(responseBody);
        // 取不到 detail 就退回 HTTP status text，讓呼叫端至少拿到「400」這種粗略資訊，而不是整條鏈
        // 因為解析失敗而換成一個更難查的例外。（getStatusText() 會拋 IOException，因此不能寫成
        // orElseGet 的 Supplier。）
        String statusText = response.getStatusText();
        String errorMessage = text(problem, DETAIL_PROPERTY).orElse(statusText);

        // 4xx 是遠端「業務上拒絕」了這次呼叫，直譯回本地的 BusinessException 交由呼叫端流程處理；
        // 其餘（5xx、非預期狀態）代表整合鏈壞了，屬於 SystemException —— 遠端狀態與訊息放進 context
        // 而非 message，因為 500 的 message 不會回給呼叫端，串進去等於丟掉。
        throw switch (response.getStatusCode()) {
            case HttpStatus.NOT_FOUND -> new BusinessException(
                    remoteErrorCode(problem).orElse(ErrorCode.RESOURCE_NOT_FOUND), errorMessage);
            case HttpStatus.BAD_REQUEST -> new BusinessException(
                    remoteErrorCode(problem).orElse(ErrorCode.INVALID_REQUEST), errorMessage);
            default -> new SystemException("下游 API 呼叫失敗")
                    .with("remoteStatus", response.getStatusCode().value())
                    .with("remoteMessage", errorMessage);
        };
    }

    /**
     * 取出下游回應裡的 {@code code} 並還原成本地的 {@link ErrorCode}；取不到就回
     * {@link Optional#empty()}，由呼叫端退回以 HTTP status 推導的預設碼。
     *
     * <p>三種情況刻意不沿用：
     * <ul>
     *   <li><b>認不出這個 code</b> —— body 被反向代理改寫過，或（日後 domain 真的拆成獨立服務時）
     *       下游版本比本版新。</li>
     *   <li><b>對應的 status 不是 4xx</b> —— {@link BusinessException} 建構子會拒絕非 4xx 的碼並拋
     *       {@code IllegalArgumentException}。這裡身處 error handler，二次爆炸會把原始錯誤換成一個
     *       更難查的 500，所以先過濾掉而不是讓它炸。</li>
     *   <li><b>下游帶了 {@code errors} 陣列</b> —— 那是逐欄位的驗證失敗，而 {@link BusinessException}
     *       承載不了那個陣列。沿用碼會讓對外回應宣稱 {@code VALIDATION_FAILED} 卻沒有 {@code errors}，
     *       違反自家契約；退回 {@code INVALID_REQUEST} 至少形狀是誠實的（語意仍在 {@code detail} 裡）。</li>
     * </ul>
     */
    private Optional<ErrorCode> remoteErrorCode(@Nullable JsonNode problem) {
        if (problem == null || !problem.path(ERRORS_PROPERTY).isMissingNode()) {
            return Optional.empty();
        }
        return text(problem, CODE_PROPERTY)
                .flatMap(RestClientErrorHandler::resolve)
                .filter(errorCode -> errorCode.getStatus().is4xxClientError());
    }

    /** {@code valueOf} 對認不出的 code 拋例外，而「認不出」在這裡是預期情況而非錯誤，故收成空值。 */
    private static Optional<ErrorCode> resolve(String code) {
        try {
            return Optional.of(ErrorCode.valueOf(code));
        } catch (IllegalArgumentException e) {
            log.warn("下游錯誤回應帶了本版不認識的 code，改用 HTTP status 推導；code={}", code);
            return Optional.empty();
        }
    }

    /** 把 tree 上一個字串欄位取成 {@link Optional}，空白值視同缺席。 */
    private Optional<String> text(@Nullable JsonNode problem, String property) {
        return (problem == null)
                ? Optional.empty()
                : problem.path(property).stringValueOpt().filter(value -> !value.isBlank());
    }

    /**
     * 把下游錯誤回應 parse 成 JSON tree；空 body 或解析失敗回 {@code null}，呼叫端一律退回以 HTTP
     * status 推導的預設值。
     *
     * <p>原本這裡完全靜默：下游改了錯誤格式時，症狀只會是「錯誤訊息忽然變成 status text」，
     * 而 log 裡查無此事。改用 WARN 記錄，才不必靠猜。
     */
    private @Nullable JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("下游錯誤回應無法解析為 JSON，改用 HTTP status text；body={}", body, e);
            return null;
        }
    }
}
