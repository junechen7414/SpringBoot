package com.ibm.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;

/**
 * 把跨 domain {@code *Client} 呼叫收到的錯誤回應，翻譯回本地的例外型別。
 *
 * <p>下游其實就是自己（loopback 自呼叫），因此收到的一定是 {@code GlobalExceptionHandler} 產的
 * RFC 9457 {@code application/problem+json}，人類可讀說明在 {@code detail} 欄位。
 *
 * <p><b>刻意讀 JSON tree 而非反序列化成型別</b>：綁定到 {@code ProblemDetail} 會連帶依賴
 * {@code ProblemDetailJacksonMixin} 有沒有註冊（沒註冊時 {@code code} 這個 extension 欄位會被當成
 * 未知屬性而整包解析失敗），綁定到自訂 record 則多一個必須跟著契約同步的型別。讀 tree 只認一個
 * 欄位名，多的欄位一律無害。
 */
@Slf4j
@Component
public class RestClientErrorHandler {

    /** RFC 9457 中「這一次請求」的人類可讀說明欄位。 */
    private static final String DETAIL_PROPERTY = "detail";

    private final ObjectMapper objectMapper;

    public RestClientErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void handle(ClientHttpResponse response) throws IOException {
        // 讀取 Body 內容 (確保只讀取一次並轉為 String)
        String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        String errorMessage = extractDetail(responseBody, response.getStatusText());

        // 4xx 是遠端「業務上拒絕」了這次呼叫，直譯回本地的 BusinessException 交由呼叫端流程處理；
        // 其餘（5xx、非預期狀態）代表整合鏈壞了，屬於 SystemException —— 遠端狀態與訊息放進 context
        // 而非 message，因為 500 的 message 不會回給呼叫端，串進去等於丟掉。
        throw switch (response.getStatusCode()) {
            case HttpStatus.NOT_FOUND -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, errorMessage);
            case HttpStatus.BAD_REQUEST -> new BusinessException(ErrorCode.INVALID_REQUEST, errorMessage);
            default -> new SystemException("下游 API 呼叫失敗")
                    .with("remoteStatus", response.getStatusCode().value())
                    .with("remoteMessage", errorMessage);
        };
    }

    /**
     * 取出下游錯誤回應的 {@code detail}；取不到就退回 HTTP status text，讓呼叫端至少拿到「400」這種
     * 粗略資訊，而不是整條鏈因為解析失敗而換成一個更難查的例外。
     */
    private String extractDetail(String body, String fallback) {
        if (body == null || body.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readTree(body)
                    .path(DETAIL_PROPERTY)
                    .stringValueOpt()
                    .filter(detail -> !detail.isBlank())
                    .orElse(fallback);
        } catch (Exception e) {
            // 原本這裡完全靜默：下游改了錯誤格式時，症狀只會是「錯誤訊息忽然變成 status text」，
            // 而 log 裡查無此事。改用 WARN 記錄，才不必靠猜。
            log.warn("下游錯誤回應無法解析為 JSON，改用 HTTP status text；body={}", body, e);
            return fallback;
        }
    }
}
