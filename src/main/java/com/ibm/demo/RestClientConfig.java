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
@EnableConfigurationProperties({AppProperties.class, HttpClientProperties.class})
public class RestClientConfig {

    private final AppProperties appProperties;
    private final HttpClientProperties httpClientProperties;

    // 透過建構子注入，這是最現代化的做法
    public RestClientConfig(AppProperties appProperties, HttpClientProperties httpClientProperties) {
        this.appProperties = appProperties;
        this.httpClientProperties = httpClientProperties;
    }

    // 建立一個自訂的 ClientHttpRequestFactory，使用 Apache HttpClient 並配置連線池和超時
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(httpClientProperties.getMaxTotal()); // 總連線池上限
        connectionManager.setDefaultMaxPerRoute(httpClientProperties.getDefaultMaxPerRoute()); // 每個單一服務 (如 Account) 的上限

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                // 關鍵：給予虛擬執行緒足夠的排隊時間，不要一拿不到連線就斷開
                .setDefaultRequestConfig(RequestConfig.custom()
                        // 從池中拿連線可以等 5 秒
                        .setConnectionRequestTimeout(Timeout.ofSeconds(httpClientProperties.getConnectionRequestTimeout()))
                        // 等待 API 回傳可等 10 秒
                        .setResponseTimeout(Timeout.ofSeconds(httpClientProperties.getResponseTimeout()))
                        .build())
                .evictIdleConnections(TimeValue.ofSeconds(httpClientProperties.getEvictIdleConnectionsPeriod()))
                .build();

        factory.setHttpClient(httpClient);
        return factory;
    }

    // 建立 HttpServiceProxyFactory，並將自訂的 ClientHttpRequestFactory 注入 RestClient
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