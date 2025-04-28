package com.ibm.demo.account;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.ibm.demo.account.DTO.GetAccountDetailResponse;

@Component
public class AccountClient {
    private final WebClient webClient;

    public AccountClient(@Qualifier("accountWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public GetAccountDetailResponse getAccountDetail(Integer accountId) {
        return webClient.get()
                .uri("/getDetail/{id}", accountId)
                .retrieve() // 取得回傳
                .bodyToMono(GetAccountDetailResponse.class) // 將ResponseBody轉換型態到Mono，而Java需要知道如何將JSON轉換為具體的類型
                .block();
    }

    public void validateActiveAccount(Integer accountId) {
        webClient.get()
                .uri("/validate/{accountId}", accountId)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }
}
