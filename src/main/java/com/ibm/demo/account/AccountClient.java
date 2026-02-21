package com.ibm.demo.account;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.ResourceNotFoundException;

@Component
public class AccountClient {
    private final RestClient restClient;

    // 假設你會在 Config 中定義一個名為 "accountRestClient" 的 RestClient Bean
    public AccountClient(@Qualifier("accountRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public GetAccountDetailResponse getAccountDetail(Integer accountId) {
        try {
            return restClient.get()
                    .uri("/getDetail/{id}", accountId)
                    .retrieve() // 取得回傳
                    .body(GetAccountDetailResponse.class); // 直接轉換為物件，無需 block()
        } catch (RestClientResponseException ex) {
            String errorMessage = extractErrorMessage(ex, "無法獲取帳戶詳細資訊");
            if (ex.getStatusCode() == HttpStatusCode.valueOf(404)) {
                throw new ResourceNotFoundException(errorMessage);
            }
            // 對於其他 4xx/5xx 錯誤，可以拋出通用錯誤或更具體的錯誤
            throw new RuntimeException("呼叫帳戶服務失敗: " + errorMessage, ex);
        }
    }

    // 輔助方法，嘗試從 RestClientResponseException 中提取下游服務的錯誤訊息
    // 從 API 呼叫失敗的例外中，優先取得對方服務回傳的具體錯誤訊息 (假設格式符合 ApiErrorResponse)。如果無法取得，就退一步使用 HTTP
    // 狀態文字。如果連 HTTP 狀態文字都怪怪的或不適用，最後就使用一個預設的通用錯誤訊息。
    private String extractErrorMessage(RestClientResponseException ex, String defaultMessage) {
        try {
            ApiErrorResponse errorResponse = ex.getResponseBodyAs(ApiErrorResponse.class);
            if (errorResponse != null && errorResponse.getMessage() != null && !errorResponse.getMessage().isEmpty()) {
                return errorResponse.getMessage();
            }
        } catch (Exception parseEx) {
            return ex.getStatusText() + " (無法解析詳細錯誤)";
        }
        return defaultMessage;
    }
}
