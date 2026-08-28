package com.ibm.demo.contract;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 錯誤回應的 <b>wire format 契約測試</b>。
 *
 * <p>存在理由：其餘測試全都在 Java 型別層斷言（直接 new handler、比對 record accessor），因此
 * 改掉 JSON 欄位名、序列化格式或 {@code Content-Type}，一個測試都不會紅 —— CI gate 對 API 契約
 * 完全無感。這裡走真實的 DispatcherServlet + Security filter chain + Jackson，斷言的是<b>呼叫端
 * 真正看到的位元組</b>。
 *
 * <p>因此本檔案的斷言刻意寫得囉唆：欄位名、欄位有無、Content-Type 都逐一釘住。任何改動錯誤契約
 * 的重構都必須先讓這裡變紅，而紅的範圍就是那次契約變更的實際範圍。
 */
@WebMvcTest
@DisplayName("錯誤回應 wire format 契約")
class ApiErrorContractTest {

    /** 目前 {@code ApiErrorResponse} 的 timestamp 序列化格式（由 {@code @JsonFormat} 指定）。 */
    private static final String TIMESTAMP_PATTERN = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    @DisplayName("成功回應不受錯誤契約影響：200 application/json、裸 DTO 無信封")
    void successResponseIsUnwrapped() throws Exception {
        mockMvc.perform(get("/contract-probe/ok"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.value").value("ok"))
                // 成功側沒有 success/data 之類的全域信封
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Nested
    @WithMockUser
    @DisplayName("GlobalExceptionHandler 自行組裝的回應（ApiErrorResponse）")
    class ApplicationHandled {

        @Test
        @DisplayName("BusinessException：status 由 ErrorCode 決定，code/error/message 三欄各司其職")
        void businessException() throws Exception {
            mockMvc.perform(get("/contract-probe/business/{code}", "PRODUCT_STOCK_NOT_ENOUGH"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.timestamp").value(matchesPattern(TIMESTAMP_PATTERN)))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("PRODUCT_003"))
                    .andExpect(jsonPath("$.error").value("商品庫存不足"))
                    .andExpect(jsonPath("$.message").value("探針訊息: PRODUCT_STOCK_NOT_ENOUGH"))
                    // 錯誤回應目前不含請求路徑 —— 呼叫端無法從 body 得知是哪個請求出錯
                    .andExpect(jsonPath("$.path").doesNotExist())
                    .andExpect(jsonPath("$.instance").doesNotExist());
        }

        @Test
        @DisplayName("BusinessException：RESOURCE_NOT_FOUND 對應 404")
        void businessExceptionNotFound() throws Exception {
            mockMvc.perform(get("/contract-probe/business/{code}", "RESOURCE_NOT_FOUND"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("SYS_001"))
                    .andExpect(jsonPath("$.error").value("找不到資源"));
        }

        @Test
        @DisplayName("Bulkhead 滿載：503，code = BULKHEAD_FULL")
        void bulkheadFull() throws Exception {
            mockMvc.perform(get("/contract-probe/bulkhead"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.code").value("BULKHEAD_FULL"))
                    .andExpect(jsonPath("$.error").value("Service Overloaded"))
                    .andExpect(jsonPath("$.message").value("系統負載過高，請稍後再試。"));
        }

        @Test
        @DisplayName("Circuit breaker 開啟：503，code = CIRCUIT_OPEN")
        void circuitOpen() throws Exception {
            mockMvc.perform(get("/contract-probe/circuit-breaker"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.code").value("CIRCUIT_OPEN"))
                    .andExpect(jsonPath("$.error").value("Circuit Breaker Open"))
                    .andExpect(jsonPath("$.message").value("服務暫時不可用，請稍後再試。"));
        }

        @Test
        @DisplayName("Rate limiter 拒絕：429，code = RATE_LIMITED")
        void rateLimited() throws Exception {
            mockMvc.perform(get("/contract-probe/rate-limiter"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                    .andExpect(jsonPath("$.error").value("Rate Limit Exceeded"))
                    .andExpect(jsonPath("$.message").value("請求過於頻繁，請稍後再試。"));
        }

        @Test
        @DisplayName("樂觀鎖衝突：409，code = OPTIMISTIC_LOCK")
        void optimisticLock() throws Exception {
            mockMvc.perform(get("/contract-probe/optimistic-lock"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"))
                    .andExpect(jsonPath("$.error").value("Optimistic Locking Failure"))
                    .andExpect(jsonPath("$.message").value("資料已被其他使用者修改，請重新整理後再試。"));
        }

        @Test
        @DisplayName("SystemException：500 且完全不透明 —— context 與 message 不得洩漏")
        void systemException() throws Exception {
            mockMvc.perform(get("/contract-probe/system-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message").value("系統發生未預期的錯誤，請稍後再試。"))
                    .andExpect(content().string(not(containsString("Bad Gateway"))))
                    .andExpect(content().string(not(containsString("502"))));
        }

        @Test
        @DisplayName("未預期例外：與 SystemException 回應完全相同（差異只該在 log）")
        void unexpectedException() throws Exception {
            mockMvc.perform(get("/contract-probe/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message").value("系統發生未預期的錯誤，請稍後再試。"))
                    .andExpect(content().string(not(containsString("探針刻意拋出"))));
        }

        @Test
        @DisplayName("欄位驗證失敗：欄位錯誤壓成單一 message 字串，global error 被丟棄")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/contract-probe/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountId\": null, \"quantity\": -1, \"min\": 10, \"max\": 1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error").value("Validation Error"))
                    // 目前沒有逐欄位的結構化陣列，呼叫端只能拿到一個人類可讀字串
                    .andExpect(jsonPath("$.errors").doesNotExist())
                    .andExpect(jsonPath("$.message").value(startsWith("參數驗證失敗: ")))
                    .andExpect(jsonPath("$.message").value(containsString("[accountId: must not be null]")))
                    .andExpect(jsonPath("$.message").value(containsString("[quantity: must be positive]")))
                    // class-level（global）約束的訊息目前被靜默丟棄
                    .andExpect(jsonPath("$.message").value(not(containsString("min must not exceed max"))));
        }
    }

    @Nested
    @WithMockUser
    @DisplayName("父類別 ResponseEntityExceptionHandler 產生的回應（RFC 9457 ProblemDetail）")
    class FrameworkHandled {

        @Test
        @DisplayName("JSON 格式錯誤：application/problem+json，欄位名與應用層完全不同")
        void malformedJson() throws Exception {
            mockMvc.perform(post("/contract-probe/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ this is not json "))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    // type 缺席即代表 RFC 9457 的預設值 about:blank —— 框架不序列化預設值，
                    // 也就是說目前沒有任何錯誤帶得出可辨識的 problem type URI
                    .andExpect(jsonPath("$.type").doesNotExist())
                    .andExpect(jsonPath("$.title").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").exists())
                    // 這條路徑沒有 code / error / message / timestamp —— 與應用層格式分岔
                    .andExpect(jsonPath("$.code").doesNotExist())
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(jsonPath("$.message").doesNotExist())
                    .andExpect(jsonPath("$.timestamp").doesNotExist());
        }

        @Test
        @DisplayName("HTTP method 不支援：405 problem+json")
        void methodNotAllowed() throws Exception {
            mockMvc.perform(post("/contract-probe/ok"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.code").doesNotExist());
        }

        @Test
        @DisplayName("Content-Type 不支援：415 problem+json")
        void unsupportedMediaType() throws Exception {
            mockMvc.perform(post("/contract-probe/validate")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("plain"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(415))
                    .andExpect(jsonPath("$.code").doesNotExist());
        }

        @Test
        @DisplayName("缺少必填 query param：400 problem+json")
        void missingRequestParameter() throws Exception {
            mockMvc.perform(get("/contract-probe/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").doesNotExist());
        }

        @Test
        @DisplayName("query param 型別錯誤：400 problem+json")
        void parameterTypeMismatch() throws Exception {
            mockMvc.perform(get("/contract-probe/required-param").param("amount", "not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").doesNotExist());
        }
    }

    @Nested
    @DisplayName("認證／授權失敗的回應（Security filter chain 委派回 GlobalExceptionHandler）")
    class SecurityHandled {

        @Test
        @DisplayName("未帶憑證：401，body 與其他錯誤同格式，且仍保留 WWW-Authenticate 挑戰")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/contract-probe/ok"))
                    .andExpect(status().isUnauthorized())
                    // 委派給 MVC 的 handlerExceptionResolver 後，Basic 的挑戰 header 要自己補回來
                    .andExpect(header().string("WWW-Authenticate", startsWith("Basic realm=")))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.timestamp").value(matchesPattern(TIMESTAMP_PATTERN)))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("需要有效的認證憑證。"));
        }

        @Test
        @DisplayName("未帶憑證：401 的 message 固定，不得洩漏 Spring Security 的帳號枚舉線索")
        void unauthenticatedMessageIsOpaque() throws Exception {
            mockMvc.perform(get("/contract-probe/ok").header("Authorization", "Basic " + basicCredentials()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("需要有效的認證憑證。"))
                    // 「找不到使用者」與「密碼錯誤」在對外回應上必須無法區分
                    .andExpect(content().string(not(containsString("Bad credentials"))))
                    .andExpect(content().string(not(containsString("no-such-user"))));
        }

        @Test
        @DisplayName("AccessDeniedException：403 而非 500 —— 方法級授權導入後不會表現成伺服器故障")
        @WithMockUser
        void accessDeniedBecomesForbidden() throws Exception {
            mockMvc.perform(get("/contract-probe/access-denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value("沒有權限執行此操作。"))
                    .andExpect(content().string(not(containsString("探針拒絕存取"))));
        }

        private String basicCredentials() {
            return Base64.getEncoder()
                    .encodeToString("no-such-user:wrong-password".getBytes(StandardCharsets.UTF_8));
        }
    }
}
