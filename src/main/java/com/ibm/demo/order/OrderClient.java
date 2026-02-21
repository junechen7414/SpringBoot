package com.ibm.demo.order;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OrderClient {
    private final RestClient restClient;

    public OrderClient(@Qualifier("orderRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 開放查看帳戶ID是否存在任何訂單中的端點
    public boolean accountIdIsInOrder(Integer accountId) {
        try {
            Boolean result = restClient.get()
                    .uri("/AccountIdIsInOrder/{accountId}", accountId)
                    .retrieve()
                    .body(Boolean.class);
            return result != null && result;
        } catch (RestClientResponseException ex) {
            throw new RuntimeException("呼叫訂單服務檢查帳戶時失敗: " + ex.getMessage(), ex);
        }
    }
}
