package com.ibm.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.BusinessLogicCheck.InvalidRequestException;
import com.ibm.demo.exception.BusinessLogicCheck.ResourceNotFoundException;

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

        // 實務上建議使用 switch 提升可讀性 (Java 17+ 語法)
        throw switch (response.getStatusCode()) {
            case HttpStatus.NOT_FOUND -> new ResourceNotFoundException(errorMessage);
            case HttpStatus.BAD_REQUEST -> new InvalidRequestException(errorMessage);
            default -> new RuntimeException("API Call Failed [" + response.getStatusCode() + "]: " + errorMessage);
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