package com.ibm.demo.downstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * 下游錯誤從 HTTP 回應一路翻成<b>最終對外回應</b>的端到端測試（不含 Oracle）。
 *
 * <p>路徑：MockMvc → probe controller → 掛 {@code @CircuitBreaker} 的 service → 正式設定的
 * {@code ProductClient}（RestClientConfig：連線池、Basic 憑證、status handler）→ 真的 HTTP 打到本機 stub →
 * {@code RestClientErrorHandler} 轉譯 → {@code GlobalExceptionHandler} 組回應。
 * {@code RestClientErrorHandlerTest} 只證明「轉成哪種例外」，這裡才證明<b>呼叫端真正收到什麼</b>，
 * 以及<b>斷路器怎麼算這一次</b>。
 *
 * <p>斷路器計數的判讀：resilience4j 對 {@code ignore-exceptions} 內的例外<b>既不記成功也不記失敗</b>
 * （根本不進統計窗口），所以「被忽略」的外顯形狀是 buffered=0，「計入故障」是 failed=1。
 * 讀的是正式 {@code application.yml}，所以有人動了 {@code ignore-exceptions}，這裡會紅。
 *
 * <p>stub 在 static 區塊啟動、埠號在 context 建立前就確定（{@link DynamicPropertySource} 把
 * {@code app.base-url} 指過去），與 {@code BaseIntegrationTest} 的單例容器同樣的理由：context 會被快取，
 * 生命週期不能綁在單一測試方法上。
 */
@WebMvcTest
@WithMockUser
@DisplayName("下游錯誤轉譯：最終 HTTP 回應與 CircuitBreaker 計數")
class DownstreamErrorTranslationTest {

    private static final String PROBE_URI = "/probe/downstream/product";

    /** stub 要回的下一個回應；每個測試開頭都會重設。 */
    private record StubResponse(int status, String contentType, String body, String extraHeader, String extraValue) {
    }

    private static volatile StubResponse next;
    private static volatile String lastAuthorization;
    private static final HttpServer STUB = startStub();

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
                StubResponse response = next;
                byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", response.contentType());
                if (response.extraHeader() != null) {
                    exchange.getResponseHeaders().add(response.extraHeader(), response.extraValue());
                }
                // 204 之類不可帶 body；其餘一律明寫長度
                exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("無法啟動下游 stub", e);
        }
    }

    @DynamicPropertySource
    static void pointLoopbackAtStub(DynamicPropertyRegistry registry) {
        registry.add("app.base-url", () -> "http://localhost:" + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker;

    @BeforeEach
    void resetBreaker() {
        breaker = circuitBreakerRegistry.circuitBreaker(DownstreamTestApplication.BREAKER_NAME);
        breaker.reset();
        lastAuthorization = null;
    }

    // ---------------------------------------------------------------- helpers

    private static void stub(int status, String body) {
        next = new StubResponse(status, "application/problem+json", body, null, null);
    }

    /** 模擬 GlobalExceptionHandler 產的下游 problem+json（含一堆不該被轉送出去的欄位）。 */
    private static String problem(int status, String code, String detail) {
        return """
                {
                  "type": "urn:problem:%s",
                  "title": "遠端標題",
                  "status": %d,
                  "detail": "%s",
                  "instance": "/remote/internal/path/77",
                  "code": "%s"
                }
                """.formatted(code.toLowerCase().replace('_', '-'), status, detail, code);
    }

    private ResultActions call() throws Exception {
        return mockMvc.perform(get(PROBE_URI));
    }

    /** 500 的共通斷言：固定安全說明，且不洩漏任何遠端內容。 */
    private static void assertOpaqueInternalError(ResultActions result) throws Exception {
        result.andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.type").value("urn:problem:internal-error"))
                .andExpect(jsonPath("$.detail").value("系統發生未預期的錯誤，請稍後再試。"))
                .andExpect(jsonPath("$.instance").value(PROBE_URI))
                .andExpect(content().string(not(containsString("遠端"))))
                .andExpect(content().string(not(containsString("/remote/internal"))));
    }

    // ---------------------------------------------------------------- 已知配對 → 保留語意

    static Stream<Arguments> knownBusinessRejections() {
        return Stream.of(
                Arguments.of(400, "ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED"),
                Arguments.of(400, "PRODUCT_ALREADY_EXIST"),
                Arguments.of(400, "PRODUCT_STILL_HAS_RESERVATION_CAN_NOT_BE_DELETED"),
                Arguments.of(400, "PRODUCT_STOCK_NOT_ENOUGH"),
                Arguments.of(404, "RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("knownBusinessRejections")
    @DisplayName("已知業務拒絕：status／code 保留、detail 保留遠端說明、instance 是外層請求、斷路器忽略")
    void knownBusinessRejection_IsPreservedAndNotCountedAsFailure(int status, String code) throws Exception {
        stub(status, problem(status, code, "商品 5 庫存不足"));

        call().andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.type").value("urn:problem:" + code.toLowerCase().replace('_', '-')))
                .andExpect(jsonPath("$.detail").value("商品 5 庫存不足"))
                // 外層 instance 代表外部這次請求，不是遠端那條內部路徑
                .andExpect(jsonPath("$.instance").value(PROBE_URI));

        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).as("業務拒絕不進統計").isZero();
    }

    @Test
    @DisplayName("內部呼叫真的帶了 internal 帳號的 Basic 憑證（RestClientConfig 接線沒被繞過）")
    void internalCallCarriesBasicCredentials() throws Exception {
        stub(404, problem(404, "RESOURCE_NOT_FOUND", "x"));

        call().andExpect(status().isNotFound());

        assertThat(lastAuthorization).isNotNull().startsWith("Basic ");
        String decoded = new String(Base64.getDecoder().decode(lastAuthorization.substring("Basic ".length())),
                StandardCharsets.UTF_8);
        assertThat(decoded).startsWith("internal:");
    }

    @Test
    @DisplayName("409 + OPTIMISTIC_LOCK_CONFLICT → 409，本地說明；不轉送遠端 detail／instance／Retry-After；斷路器忽略")
    void remoteConflict_IsTranslatedWithLocalDetail() throws Exception {
        next = new StubResponse(409, "application/problem+json", """
                {"status": 409, "code": "OPTIMISTIC_LOCK_CONFLICT", "detail": "遠端內部 SQL 細節",
                 "instance": "/remote/internal/path/77"}
                """, "Retry-After", "30");

        call().andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("資料已被其他使用者修改，請重新整理後再試。"))
                .andExpect(jsonPath("$.instance").value(PROBE_URI))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(content().string(not(containsString("遠端"))));

        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).as("409 是預期結果，不進統計").isZero();
    }

    @ParameterizedTest(name = "503 + {0}")
    @ValueSource(strings = { "BULKHEAD_FULL", "CIRCUIT_OPEN" })
    @DisplayName("503 + 滿載／熔斷 → 503，本地說明；不轉送遠端內容與 Retry-After；依賴真的不可用，斷路器計入失敗")
    void remoteUnavailable_IsTranslatedAndCountedAsFailure(String code) throws Exception {
        next = new StubResponse(503, "application/problem+json",
                problem(503, code, "遠端內部細節 host-17"), "Retry-After", "30");
        String expectedDetail = code.equals("BULKHEAD_FULL")
                ? "系統負載過高，請稍後再試。"
                : "服務暫時不可用，請稍後再試。";

        call().andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.type").value("urn:problem:" + code.toLowerCase().replace('_', '-')))
                .andExpect(jsonPath("$.detail").value(expectedDetail))
                .andExpect(jsonPath("$.instance").value(PROBE_URI))
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(content().string(not(containsString("host-17"))))
                .andExpect(content().string(not(containsString("/remote/internal"))));

        assertThat(breaker.getMetrics().getNumberOfFailedCalls())
                .as("改善對外 status 不代表把依賴故障改算成功").isEqualTo(1);
    }

    // ---------------------------------------------------------------- 不可轉譯 → 安全 500、計入故障

    static Stream<Arguments> unknownOrContradictoryResponses() {
        return Stream.of(
                Arguments.of("未知 code 的 404", 404, problem(404, "SOME_FUTURE_CODE", "遠端說明")),
                Arguments.of("未知 code 的 400", 400, problem(400, "SOME_FUTURE_CODE", "遠端說明")),
                Arguments.of("非 JSON 的 404（例如 URL 打錯）", 404, "<html>遠端 404 Not Found</html>"),
                Arguments.of("空 body 的 400", 400, ""),
                Arguments.of("JSON 但不是 object", 400, "[\"遠端\"]"),
                Arguments.of("泛用 INVALID_REQUEST", 400, problem(400, "INVALID_REQUEST", "遠端說明")),
                Arguments.of("遠端驗證失敗（帶 errors）", 400, """
                        {"status": 400, "code": "VALIDATION_FAILED", "detail": "遠端驗證",
                         "errors": [{"field": "quantity", "message": "遠端訊息"}]}"""),
                Arguments.of("HTTP 400 卻帶 RESOURCE_NOT_FOUND（code 不能改變最終 status）", 400,
                        problem(400, "RESOURCE_NOT_FOUND", "遠端說明")),
                Arguments.of("body.status 與 HTTP status 矛盾", 400,
                        problem(404, "PRODUCT_STOCK_NOT_ENOUGH", "遠端說明")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unknownOrContradictoryResponses")
    @DisplayName("未知／矛盾／不支援的回應 → 安全 500，且計入斷路器失敗（不再被猜成業務拒絕而被忽略）")
    void unknownResponse_BecomesOpaque500AndCountsAsFailure(String ignoredLabel, int status, String body)
            throws Exception {
        stub(status, body);

        assertOpaqueInternalError(call());

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @ParameterizedTest(name = "下游 {0}")
    @ValueSource(ints = { 401, 403, 405, 415, 429, 500, 502, 504 })
    @DisplayName("下游 401／403／405／415／429 與其他 5xx → 仍是安全 500，不照抄成外部呼叫端的問題")
    void unsupportedStatuses_StayOpaque500(int status) throws Exception {
        stub(status, problem(status, "RESOURCE_NOT_FOUND", "遠端說明"));

        assertOpaqueInternalError(call());

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 成功路徑基準

    @Test
    @DisplayName("基準：下游回 200 時照常通過，並計為成功（證明上面的 0 筆統計不是因為 stub 或接線壞掉）")
    void success_PassesThroughAndCountsAsSuccess() throws Exception {
        next = new StubResponse(200, MediaType.APPLICATION_JSON_VALUE, "[]", null, null);

        call().andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));

        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }
}
