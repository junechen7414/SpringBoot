package com.ibm.demo.order;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OrderClient {
    private final WebClient webClient;

    public OrderClient(@Qualifier("orderWebClient") WebClient webClientBuilder) {
        this.webClient = webClientBuilder;
    }

    // 開放查看帳戶ID是否存在任何訂單中的端點
    public boolean accountIdIsInOrder(Integer accountId) {
        return webClient.get()
                .uri("/AccountIdIsInOrder/{accountId}", accountId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .block();
    }
}
