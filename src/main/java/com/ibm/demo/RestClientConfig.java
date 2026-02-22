package com.ibm.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.demo.account.AccountClient;
import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.order.OrderClient;
import com.ibm.demo.product.ProductClient;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class RestClientConfig {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    // 透過建構子注入，這是最現代化的做法
    public RestClientConfig(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    private HttpServiceProxyFactory createProxyFactory(RestClient.Builder builder) {
        RestClient restClient = builder
                .baseUrl(appProperties.getBaseUrl()) // 改從 appProperties 取得
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    // ... 之前的錯誤處理邏輯保持不變
                    ApiErrorResponse error = null;
                    try {
                        error = objectMapper.readValue(response.getBody(), ApiErrorResponse.class);
                    } catch (Exception e) {
                    }

                    String msg = (error != null && error.getMessage() != null)
                            ? error.getMessage()
                            : response.getStatusText();

                    if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new ResourceNotFoundException(msg);
                    } else if (response.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                        throw new InvalidRequestException(msg);
                    }
                    throw new RuntimeException("API Call Failed: " + msg);
                })
                .build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    }

    @Bean
    public AccountClient accountClient(RestClient.Builder builder) {
        return createProxyFactory(builder).createClient(AccountClient.class);
    }

    @Bean
    public OrderClient orderClient(RestClient.Builder builder) {
        return createProxyFactory(builder).createClient(OrderClient.class);
    }

    @Bean
    public ProductClient productClient(RestClient.Builder builder) {
        return createProxyFactory(builder).createClient(ProductClient.class);
    }
}