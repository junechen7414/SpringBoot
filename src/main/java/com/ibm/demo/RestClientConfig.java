package com.ibm.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.ibm.demo.account.AccountClient;
import com.ibm.demo.order.OrderClient;
import com.ibm.demo.product.ProductClient;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class RestClientConfig {

    private final AppProperties appProperties;

    // 透過建構子注入，這是最現代化的做法
    public RestClientConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(RestClient.Builder builder,
            RestClientErrorHandler errorHandler) {
        RestClient restClient = builder
                .baseUrl(appProperties.getBaseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> errorHandler.handle(response))
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    }

    @Bean
    public AccountClient accountClient(HttpServiceProxyFactory factory) {
        return factory.createClient(AccountClient.class);
    }

    @Bean
    public OrderClient orderClient(HttpServiceProxyFactory factory) {
        return factory.createClient(OrderClient.class);
    }

    @Bean
    public ProductClient productClient(HttpServiceProxyFactory factory) {
        return factory.createClient(ProductClient.class);
    }
}