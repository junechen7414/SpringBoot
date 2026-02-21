package com.ibm.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean(name = "accountRestClient")
    public RestClient accountRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl("http://localhost:8087/account")
                .build();
    }

    @Bean(name = "orderRestClient")
    public RestClient orderRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl("http://localhost:8087/order")
                .build();
    }

    @Bean(name = "productRestClient")
    public RestClient productRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl("http://localhost:8087/product")
                .build();
    }
}
