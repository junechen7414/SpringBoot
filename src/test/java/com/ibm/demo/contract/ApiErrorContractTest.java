package com.ibm.demo.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.ValidationError;

import tools.jackson.databind.json.JsonMapper;

/**
 * 錯誤回應的 <b>wire format 契約測試</b>。
 *
 * <p>存在理由：其餘測試全都在 Java 型別層斷言（直接 new handler、比對 {@code ProblemDetail} 的
 * getter），因此改掉 JSON 欄位名、序列化格式或 {@code Content-Type}，一個測試都不會紅 —— CI gate
 * 對 API 契約完全無感。這裡走真實的 DispatcherServlet + Security filter chain + Jackson，斷言的是
 * <b>呼叫端真正看到的位元組</b>。
 *
 * <p>因此本檔案的斷言刻意寫得囉唆：欄位名、欄位有無、Content-Type 都逐一釘住。任何改動錯誤契約
 * 的重構都必須先讓這裡變紅，而紅的範圍就是那次契約變更的實際範圍。
 *
 * <p>契約本體是 RFC 9457（{@code application/problem+json}）：{@code type} / {@code title} /
 * {@code status} / {@code detail} / {@code instance} 五個標準欄位，加上兩個 extension —— 一律出現的
 * {@code code}，以及只在驗證失敗時出現的 {@code errors}。「應用層攔到的」與「框架攔到的」兩條路徑
 * 必須產出同一組欄位 —— 呼叫端無法預測例外是誰攔到的，格式就不能因此分岔。
 */
@WebMvcTest
@DisplayName("錯誤回應 wire format 契約")
class ApiErrorContractTest {

    /** RFC 9457 標準欄位 + 每個錯誤都有的 extension；順序無意義，只比對集合。 */
    private static final List<String> PROBLEM_FIELDS = List.of("type", "title", "status", "detail", "instance", "code");

    /** 驗證失敗多一個 {@code errors} —— 這是線路上會出現的欄位「上界」。 */
    private static final List<String> VALIDATION_FIELDS = Stream.concat(PROBLEM_FIELDS.stream(), Stream.of("errors"))
            .toList();

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

    @Test
    @WithMockUser
    @DisplayName("OpenAPI 用的 ApiErrorResponse schema 必須與線路上的欄位完全一致")
    void documentedSchemaMatchesWireFormat() throws Exception {
        // ApiErrorResponse 是給 springdoc 看的「影子」型別，不參與序列化 —— 因此它會不會說謊，
        // 只能靠這裡比對。漂移在這裡變紅，而不是等下游 codegen 少了一個欄位才發現。
        List<String> businessFields = wireFields(mockMvc.perform(
                get("/contract-probe/business/{code}", "PRODUCT_STOCK_NOT_ENOUGH"))
                .andExpect(status().isBadRequest()));
        List<String> validationFields = wireFields(mockMvc.perform(post("/contract-probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": null, \"quantity\": -1, \"min\": 10, \"max\": 1}"))
                .andExpect(status().isBadRequest()));

        List<String> documentedFields = Arrays.stream(ApiErrorResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        // 一般錯誤沒有 errors —— 是欄位缺席，不是空陣列（空陣列會讓呼叫端誤以為「有驗證錯誤但沒細節」）
        assertThat(businessFields).containsExactlyInAnyOrderElementsOf(PROBLEM_FIELDS);
        assertThat(validationFields).containsExactlyInAnyOrderElementsOf(VALIDATION_FIELDS);
        // 文件宣告的欄位集合必須恰好等於線路上可能出現的欄位聯集：多宣告是說謊，少宣告是漏文件
        assertThat(documentedFields).containsExactlyInAnyOrderElementsOf(validationFields);
    }

    @Test
    @WithMockUser
    @DisplayName("errors 元素的欄位也必須與 ValidationError 宣告一致")
    void documentedValidationErrorMatchesWireFormat() throws Exception {
        String body = mockMvc.perform(post("/contract-probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": null, \"quantity\": 1, \"min\": 1, \"max\": 2}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        List<String> wireFields = JsonMapper.builder().build().readTree(body).path("errors").get(0)
                .propertyNames().stream().toList();
        List<String> documentedFields = Arrays.stream(ValidationError.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(documentedFields).containsExactlyInAnyOrderElementsOf(List.of("field", "message"));
        assertThat(wireFields).containsExactlyInAnyOrderElementsOf(documentedFields);
    }

    /** 取回應 body 的頂層 JSON key —— 契約比對的是欄位「有哪些」，不是值。 */
    private List<String> wireFields(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonMapper.builder().build().readTree(body).propertyNames().stream().toList();
    }

    @Nested
    @WithMockUser
    @DisplayName("GlobalExceptionHandler 自行組裝的回應")
    class ApplicationHandled {

        @Test
        @DisplayName("BusinessException：status 由 ErrorCode 決定，type/title/detail/code 各司其職")
        void businessException() throws Exception {
            mockMvc.perform(get("/contract-probe/business/{code}", "PRODUCT_STOCK_NOT_ENOUGH"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    // type 由 code 機械推導，兩者結構上不可能不一致
                    .andExpect(jsonPath("$.type").value("urn:problem:product-stock-not-enough"))
                    .andExpect(jsonPath("$.title").value("商品庫存不足"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("探針訊息: PRODUCT_STOCK_NOT_ENOUGH"))
                    // instance 由 Spring 自動填入請求路徑 —— 呼叫端從 body 就看得出是哪個請求出錯
                    .andExpect(jsonPath("$.instance").value("/contract-probe/business/PRODUCT_STOCK_NOT_ENOUGH"))
                    .andExpect(jsonPath("$.code").value("PRODUCT_STOCK_NOT_ENOUGH"))
                    // 舊契約的欄位一律不得殘留（timestamp 是客戶端時鐘該負責的事，Date header 已經有了）
                    .andExpect(jsonPath("$.timestamp").doesNotExist())
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(jsonPath("$.message").doesNotExist())
                    .andExpect(jsonPath("$.path").doesNotExist());
        }

        @Test
        @DisplayName("BusinessException：RESOURCE_NOT_FOUND 對應 404")
        void businessExceptionNotFound() throws Exception {
            mockMvc.perform(get("/contract-probe/business/{code}", "RESOURCE_NOT_FOUND"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.type").value("urn:problem:resource-not-found"))
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.title").value("找不到資源"));
        }

        @Test
        @DisplayName("Bulkhead 滿載：503，code = BULKHEAD_FULL")
        void bulkheadFull() throws Exception {
            mockMvc.perform(get("/contract-probe/bulkhead"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.code").value("BULKHEAD_FULL"))
                    .andExpect(jsonPath("$.title").value("系統負載過高"))
                    .andExpect(jsonPath("$.detail").value("系統負載過高，請稍後再試。"));
        }

        @Test
        @DisplayName("Circuit breaker 開啟：503，code = CIRCUIT_OPEN")
        void circuitOpen() throws Exception {
            mockMvc.perform(get("/contract-probe/circuit-breaker"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.code").value("CIRCUIT_OPEN"))
                    .andExpect(jsonPath("$.title").value("服務暫時不可用"))
                    .andExpect(jsonPath("$.detail").value("服務暫時不可用，請稍後再試。"));
        }

        @Test
        @DisplayName("Rate limiter 拒絕：429，code = RATE_LIMITED")
        void rateLimited() throws Exception {
            mockMvc.perform(get("/contract-probe/rate-limiter"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                    .andExpect(jsonPath("$.title").value("請求過於頻繁"))
                    .andExpect(jsonPath("$.detail").value("請求過於頻繁，請稍後再試。"));
        }

        @Test
        @DisplayName("樂觀鎖衝突：409，code = OPTIMISTIC_LOCK_CONFLICT")
        void optimisticLock() throws Exception {
            mockMvc.perform(get("/contract-probe/optimistic-lock"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"))
                    .andExpect(jsonPath("$.title").value("資料版本衝突"))
                    .andExpect(jsonPath("$.detail").value("資料已被其他使用者修改，請重新整理後再試。"));
        }

        @Test
        @DisplayName("SystemException：500 且完全不透明 —— context 與 message 不得洩漏")
        void systemException() throws Exception {
            mockMvc.perform(get("/contract-probe/system-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.title").value("伺服器內部錯誤"))
                    .andExpect(jsonPath("$.detail").value("系統發生未預期的錯誤，請稍後再試。"))
                    .andExpect(content().string(not(containsString("Bad Gateway"))))
                    .andExpect(content().string(not(containsString("502"))));
        }

        @Test
        @DisplayName("未預期例外：與 SystemException 回應完全相同（差異只該在 log）")
        void unexpectedException() throws Exception {
            mockMvc.perform(get("/contract-probe/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.title").value("伺服器內部錯誤"))
                    .andExpect(jsonPath("$.detail").value("系統發生未預期的錯誤，請稍後再試。"))
                    .andExpect(content().string(not(containsString("探針刻意拋出"))));
        }

        @Test
        @DisplayName("@Valid @RequestBody 驗證失敗：errors 逐筆列出欄位，global error 也在其中")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/contract-probe/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountId\": null, \"quantity\": -1, \"min\": 10, \"max\": 1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.title").value("參數驗證失敗"))
                    // 結構化陣列讓呼叫端能把訊息掛回對應的輸入欄位，不必剖 detail 字串
                    .andExpect(jsonPath("$.errors.length()").value(3))
                    .andExpect(jsonPath("$.errors[?(@.field == 'accountId')].message")
                            .value(contains("must not be null")))
                    .andExpect(jsonPath("$.errors[?(@.field == 'quantity')].message")
                            .value(contains("must be positive")))
                    // class-level 約束不屬於任何欄位 —— field 缺席（而非 null 或空字串）
                    .andExpect(jsonPath("$.errors[?(@.message == 'min must not exceed max')].field")
                            .value(empty()))
                    // detail 與 errors 同源：三筆都在，跨 domain 呼叫只取 detail 時也不會漏資訊
                    .andExpect(jsonPath("$.detail").value(startsWith("參數驗證失敗: ")))
                    .andExpect(jsonPath("$.detail").value(containsString("accountId must not be null")))
                    .andExpect(jsonPath("$.detail").value(containsString("quantity must be positive")))
                    .andExpect(jsonPath("$.detail").value(containsString("min must not exceed max")));
        }

        @Test
        @DisplayName("@RequestParam 上的約束失敗：走另一個例外型別，但回出同一種形狀")
        void parameterValidationFailure() throws Exception {
            mockMvc.perform(get("/contract-probe/param-validate").param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    // 與 @Valid @RequestBody 完全相同的 code/title —— 呼叫端不必知道 Spring 用哪個機制檢查
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.title").value("參數驗證失敗"))
                    .andExpect(jsonPath("$.type").value("urn:problem:validation-failed"))
                    .andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value("page"))
                    .andExpect(jsonPath("$.errors[0].message").value("must be positive"))
                    .andExpect(jsonPath("$.detail").value("參數驗證失敗: page must be positive"));
        }
    }

    @Nested
    @WithMockUser
    @DisplayName("父類別 ResponseEntityExceptionHandler 產生的回應")
    class FrameworkHandled {

        @Test
        @DisplayName("JSON 格式錯誤：problem+json，且與應用層同樣帶得出 code 與 type")
        void malformedJson() throws Exception {
            mockMvc.perform(post("/contract-probe/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ this is not json "))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    // 這批協定層錯誤沒有對應的 ErrorCode，code 取 HTTP 狀態名 —— 但欄位一定有值，
                    // 呼叫端不必為了「框架攔到的」另寫一套分流邏輯
                    .andExpect(jsonPath("$.type").value("urn:problem:bad-request"))
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.title").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").exists())
                    .andExpect(jsonPath("$.instance").value("/contract-probe/validate"))
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(jsonPath("$.message").doesNotExist())
                    .andExpect(jsonPath("$.timestamp").doesNotExist());
        }

        @Test
        @DisplayName("HTTP method 不支援：405 problem+json，code = METHOD_NOT_ALLOWED")
        void methodNotAllowed() throws Exception {
            mockMvc.perform(post("/contract-probe/ok"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.type").value("urn:problem:method-not-allowed"))
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("Content-Type 不支援：415 problem+json，code = UNSUPPORTED_MEDIA_TYPE")
        void unsupportedMediaType() throws Exception {
            mockMvc.perform(post("/contract-probe/validate")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("plain"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(415))
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        }

        @Test
        @DisplayName("缺少必填 query param：400 problem+json，code = BAD_REQUEST")
        void missingRequestParameter() throws Exception {
            mockMvc.perform(get("/contract-probe/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("query param 型別錯誤：400 problem+json，code = BAD_REQUEST")
        void parameterTypeMismatch() throws Exception {
            mockMvc.perform(get("/contract-probe/required-param").param("amount", "not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
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
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.type").value("urn:problem:unauthorized"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.title").value("需要認證"))
                    .andExpect(jsonPath("$.detail").value("需要有效的認證憑證。"))
                    .andExpect(jsonPath("$.timestamp").doesNotExist());
        }

        @Test
        @DisplayName("未帶憑證：401 的 detail 固定，不得洩漏 Spring Security 的帳號枚舉線索")
        void unauthenticatedMessageIsOpaque() throws Exception {
            mockMvc.perform(get("/contract-probe/ok").header("Authorization", "Basic " + basicCredentials()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("需要有效的認證憑證。"))
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
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.type").value("urn:problem:forbidden"))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.title").value("權限不足"))
                    .andExpect(jsonPath("$.detail").value("沒有權限執行此操作。"))
                    .andExpect(content().string(not(containsString("探針拒絕存取"))));
        }

        private String basicCredentials() {
            return Base64.getEncoder()
                    .encodeToString("no-such-user:wrong-password".getBytes(StandardCharsets.UTF_8));
        }
    }
}
