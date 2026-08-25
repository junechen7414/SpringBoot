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

    private String errorBody(int status, String code, String error, String message) {
        return """
                {
                  "timestamp": "2026-08-25 12:00:00",
                  "status": %d,
                  "code": "%s",
                  "error": "%s",
                  "message": "%s"
                }
                """.formatted(status, code, error, message);
    }

    @Test
    @DisplayName("遠端回 404，應翻成 RESOURCE_NOT_FOUND 的 BusinessException 並沿用遠端訊息")
    void handle_RemoteNotFound_ShouldThrowResourceNotFound() throws IOException {
        ClientHttpResponse response = responseWith(HttpStatus.NOT_FOUND,
                errorBody(404, "SYS_001", "找不到資源", "Account not found with id: 99"), "Not Found");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account not found with id: 99")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("遠端回 400，應翻成 INVALID_REQUEST 的 BusinessException")
    void handle_RemoteBadRequest_ShouldThrowInvalidRequest() throws IOException {
        ClientHttpResponse response = responseWith(HttpStatus.BAD_REQUEST,
                errorBody(400, "PRODUCT_003", "商品庫存不足", "商品 5 庫存不足"), "Bad Request");

        assertThatThrownBy(() -> errorHandler.handle(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商品 5 庫存不足")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("遠端回 5xx，應拋 SystemException，遠端細節進 context 而非 message")
    void handle_RemoteServerError_ShouldThrowSystemExceptionWithContext() throws IOException {
        ClientHttpResponse response = responseWith(HttpStatus.BAD_GATEWAY,
                errorBody(502, "INTERNAL_ERROR", "Internal Server Error", "系統發生未預期的錯誤，請稍後再試。"),
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
}
