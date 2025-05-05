package com.ibm.demo.account;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;

@Component
public class AccountClient {
    private final WebClient webClient;

    public AccountClient(@Qualifier("accountWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public GetAccountDetailResponse getAccountDetail(Integer accountId) {
        try {
            return webClient.get()
                    .uri("/{id}", accountId)
                    .retrieve() // 取得回傳
                    .bodyToMono(GetAccountDetailResponse.class) // 將ResponseBody轉換型態到Mono，而Java需要知道如何將JSON轉換為具體的類型
                    .block();
        } catch (WebClientResponseException ex) {
            String errorMessage = extractErrorMessage(ex, "無法獲取帳戶詳細資訊");
            if (ex.getStatusCode() == HttpStatusCode.valueOf(404)) {
                throw new ResourceNotFoundException(errorMessage);
            }
            // 對於其他 4xx/5xx 錯誤，可以拋出通用錯誤或更具體的錯誤
            throw new RuntimeException("呼叫帳戶服務失敗: " + errorMessage, ex);
        }
    }

    public void validateActiveAccount(Integer accountId) {
        try {
            webClient.get()
                    .uri("/validate/{accountId}", accountId)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException ex) {
            String errorMessage = extractErrorMessage(ex, "帳戶驗證失敗");
            if (ex.getStatusCode() == HttpStatusCode.valueOf(404)) {
                throw new ResourceNotFoundException(errorMessage);
            } else if (ex.getStatusCode() == HttpStatusCode.valueOf(400)) {
                // 回傳400表示帳戶模組拋出例外
                throw new AccountInactiveException(errorMessage);
            }
            // 對於其他 4xx/5xx 錯誤
            throw new RuntimeException("呼叫帳戶服務驗證失敗: " + errorMessage, ex);
        }
    }

    public void validateAccountExist(Integer accountId) {
        webClient.get()
                .uri("/exist/{accountId}", accountId)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    // 輔助方法，嘗試從 WebClientResponseException 中提取下游服務的錯誤訊息
    // 從 API 呼叫失敗的例外中，優先取得對方服務回傳的具體錯誤訊息 (假設格式符合 ApiErrorResponse)。如果無法取得，就退一步使用 HTTP
    // 狀態文字。如果連 HTTP 狀態文字都怪怪的或不適用，最後就使用一個預設的通用錯誤訊息。
    private String extractErrorMessage(WebClientResponseException ex, String defaultMessage) {
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
