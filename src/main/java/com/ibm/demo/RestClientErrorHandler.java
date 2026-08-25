package com.ibm.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import tools.jackson.databind.ObjectMapper;
import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;

@Component
public class RestClientErrorHandler {

    private final ObjectMapper objectMapper;

    public RestClientErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void handle(ClientHttpResponse response) throws IOException {
        // 讀取 Body 內容 (確保只讀取一次並轉為 String)
        String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        String errorMessage = extractMessage(responseBody, response.getStatusText());

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

    private String extractMessage(String body, String defaultMessage) {
        try {
            if (body != null && !body.isBlank()) {
                ApiErrorResponse error = objectMapper.readValue(body, ApiErrorResponse.class);
                if (error != null && error.message() != null) {
                    return error.message();
                }
            }
        } catch (Exception e) {
            // 解析失敗時，回傳原始狀態訊息，確保系統強健性
        }
        return defaultMessage;
    }
}