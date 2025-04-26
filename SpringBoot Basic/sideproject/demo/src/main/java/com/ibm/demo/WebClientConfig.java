package com.ibm.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean(name = "accountWebClient")
    public WebClient accountWebClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8087/api/accounts")
                .build();
    }

    @Bean(name = "orderWebClient")
    public WebClient orderWebClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8087/api/orders")
                .build();
    }

    @Bean(name = "productWebClient")
    public WebClient productWebClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8087/api/products")
                .build();
    }
}