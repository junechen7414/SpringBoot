package com.ibm.demo.account;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;
import com.ibm.demo.exception.NotFound.AccountNotFoundException;

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
                throw new AccountNotFoundException(errorMessage);
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
                throw new AccountNotFoundException(errorMessage);
            } else if (ex.getStatusCode() == HttpStatusCode.valueOf(400)) {
                // 根據帳戶服務的邏輯，400 通常意味著帳戶非啟用
                throw new AccountInactiveException(errorMessage); // 或者使用 InvalidRequestException
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
