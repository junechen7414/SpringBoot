package com.ibm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

import tools.jackson.databind.json.JsonMapper;

import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;

/**
 * {@link RestClientErrorHandler} 的單元測試。
 *
 * <p>重點在**遠端狀態如何翻譯成本地例外型別**：4xx 是遠端業務上拒絕了呼叫（BusinessException，
 * 呼叫端流程可處理），其餘代表整合鏈壞了（SystemException，需要有人去看）。翻錯型別的後果是
 * 記錄等級也跟著錯 —— 下游掛掉被記成 WARN，或業務拒絕洗出一堆 ERROR。
 *
 * <p>另一組重點是**業務碼要跨得過這一跳**：{@code code} 沿用下游的值，跨不跨 {@code *Client} 邊界
 * 不該改變對外的錯誤碼。以下四種情況刻意退回以 HTTP status 推導的預設碼，各有一個測試釘住 ——
 * 認不出的 code、非 4xx 的 code、帶 {@code errors} 陣列的驗證失敗、沒有 {@code code} 欄位。
 *
 * <p>{@code JsonMapper.builder().build()} 是刻意的**裸** mapper（沒有 Spring Boot 註冊的
 * {@code ProblemDetailJacksonMixin}）：真正的 {@code RestClient} 也可能拿到未註冊 mixin 的 mapper，
 * 而讀 JSON tree 不依賴任何 mixin —— 用裸 mapper 測，才測得到這件事。
 */
public class RestClientErrorHandlerTest {

    private final RestClientErrorHandler errorHandler = new RestClientErrorHandler(JsonMapper.builder().build());

    private ClientHttpResponse responseWith(HttpStatus status, String body, String statusText) throws IOException {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getStatusCode()).thenReturn(status);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(response.getStatusText()).thenReturn(statusText);
        return response;
    }

    /** 下游是自己，因此收到的一定是 {@code GlobalExceptionHandler} 產的 RFC 9457 problem+json。 */
    private String problemBody(int status, String code, String title, String detail) {
        return """
                {
                  "type": "urn:problem:%s",
                  "title": "%s",
                  "status": %d,
                  "detail": "%s",
                  "instance": "/account/99",
                  "code": "%s"
                }
                """.formatted(code.toLowerCase().replace('_', '-'), title, status, detail, code);
    }

    @Test
    @DisplayName("遠端回 404，應翻成 RESOURCE_NOT_FOUND 的 BusinessException 並沿用遠端 detail")
    void handle_RemoteNotFound_ShouldThrowResourceNotFound() throws IOException {
        ClientHttpResponse response = responseWith(HttpStatus.NOT_FOUND,
                problemBody(404, "RESOURCE_NOT_FOUND", "找不到資源", "Account not found with id: 99"), "Not Found");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account not found with id: 99")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("遠端回 400，應沿用下游的業務碼而非壓成 INVALID_REQUEST")
    void handle_RemoteBadRequest_ShouldPreserveRemoteBusinessCode() throws IOException {
        // 這是本檔案最重要的一條：product domain 的 PRODUCT_STOCK_NOT_ENOUGH 經 ProductClient 回到
        // order domain 後若被重建成 INVALID_REQUEST，呼叫端在 POST /order 上就無法把「庫存不足」與
        // 「訂單內重複商品」（order 本地拋的真正 INVALID_REQUEST）分開。
        ClientHttpResponse response = responseWith(HttpStatus.BAD_REQUEST,
                problemBody(400, "PRODUCT_STOCK_NOT_ENOUGH", "商品庫存不足", "商品 5 庫存不足"), "Bad Request");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商品 5 庫存不足")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);
    }

    @Test
    @DisplayName("下游 code 是本版認不出的字串時，退回以 HTTP status 推導的預設碼")
    void handle_UnknownRemoteCode_ShouldFallBackToStatusDerivedCode() throws IOException {
        // body 被反向代理改寫過，或（日後 domain 拆成獨立服務時）下游版本比本版新
        ClientHttpResponse response = responseWith(HttpStatus.BAD_REQUEST,
                problemBody(400, "SOME_FUTURE_CODE", "未來的錯誤", "下游說不行"), "Bad Request");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("下游說不行")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("下游 code 對應到非 4xx 時退回預設碼，不得讓 BusinessException 建構子炸在 error handler 裡")
    void handle_NonClientErrorRemoteCode_ShouldFallBackInsteadOfThrowing() throws IOException {
        // 狀態列是 400 但 code 寫著 INTERNAL_ERROR（500）—— 直接餵給 BusinessException 會拋
        // IllegalArgumentException，把原始錯誤換成一個更難查的 500。
        ClientHttpResponse response = responseWith(HttpStatus.BAD_REQUEST,
                problemBody(400, "INTERNAL_ERROR", "伺服器內部錯誤", "被改寫過的 body"), "Bad Request");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("被改寫過的 body")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("下游帶 errors 陣列時退回 INVALID_REQUEST，不宣稱 VALIDATION_FAILED 卻少了 errors")
    void handle_RemoteValidationFailure_ShouldNotClaimValidationFailedWithoutErrors() throws IOException {
        // BusinessException 承載不了 errors 陣列，沿用碼會讓對外回應違反自家契約
        // （code=VALIDATION_FAILED 就該附 errors[]）。語意仍留在 detail 裡。
        String body = """
                {
                  "type": "urn:problem:validation-failed",
                  "title": "參數驗證失敗",
                  "status": 400,
                  "detail": "參數驗證失敗: quantity 必須大於 0",
                  "instance": "/product/reserve",
                  "code": "VALIDATION_FAILED",
                  "errors": [{"field": "quantity", "message": "必須大於 0"}]
                }
                """;
        ClientHttpResponse response = responseWith(HttpStatus.BAD_REQUEST, body, "Bad Request");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("參數驗證失敗: quantity 必須大於 0")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("遠端回 5xx，應拋 SystemException，遠端細節進 context 而非 message")
    void handle_RemoteServerError_ShouldThrowSystemExceptionWithContext() throws IOException {
        ClientHttpResponse response = responseWith(HttpStatus.BAD_GATEWAY,
                problemBody(502, "INTERNAL_ERROR", "伺服器內部錯誤", "系統發生未預期的錯誤，請稍後再試。"),
                "Bad Gateway");

        SystemException thrown = catchThrowableOfType(SystemException.class, () -> errorHandler.handle(response));

        assertThat(thrown).isNotNull();
        // message 固定，因為 500 的 message 不會回給呼叫端 —— 細節串進去等於丟掉
        assertThat(thrown).hasMessage("下游 API 呼叫失敗");
        assertThat(thrown.getContext()).containsExactly(
                entry("remoteStatus", 502),
                entry("remoteMessage", "系統發生未預期的錯誤，請稍後再試。"));
        // 這條路徑要有 stack trace 才排查得動（BusinessException 刻意沒有）
        assertThat(thrown.getStackTrace()).isNotEmpty();
    }

    @Test
    @DisplayName("遠端 body 無法解析時，應退回 HTTP statusText，不因解析失敗而遮蔽原始錯誤")
    void handle_UnparseableBody_ShouldFallBackToStatusText() throws IOException {
        ClientHttpResponse response = responseWith(HttpStatus.NOT_FOUND, "<html>404 Not Found</html>", "Not Found");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Not Found");
    }

    @Test
    @DisplayName("body 是合法 JSON 但沒有 detail／code 欄位時，兩者都退回預設，不得炸在解析上")
    void handle_JsonWithoutDetail_ShouldFallBackToStatusText() throws IOException {
        // 例如經過反向代理改寫、或下游哪天換了錯誤格式
        ClientHttpResponse response = responseWith(HttpStatus.NOT_FOUND,
                "{\"message\": \"舊格式\", \"error\": \"Not Found\"}", "Not Found");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Not Found")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("body 為空時退回 statusText 與預設碼")
    void handle_EmptyBody_ShouldFallBackToStatusText() throws IOException {
        ClientHttpResponse response = responseWith(HttpStatus.BAD_REQUEST, "", "Bad Request");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bad Request")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
