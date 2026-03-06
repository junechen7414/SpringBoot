package com.ibm.demo;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
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

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(2000); // 總連線池上限
        connectionManager.setDefaultMaxPerRoute(1500); // 每個單一服務 (如 Account) 的上限

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                // 關鍵：給予虛擬執行緒足夠的排隊時間，不要一拿不到連線就斷開
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5)) // 從池中拿連線可以等 5 秒
                        .setResponseTimeout(Timeout.ofSeconds(10)) // 等待 API 回傳可等 10 秒
                        .build())
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();

        factory.setHttpClient(httpClient);
        return factory;
    }

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(RestClient.Builder builder,
            RestClientErrorHandler errorHandler) {
        RestClient restClient = builder
                .requestFactory(clientHttpRequestFactory()) // 顯式指定底層引擎
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