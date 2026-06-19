package com.ibm.demo.config;

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
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.ibm.demo.RestClientErrorHandler;
import com.ibm.demo.account.AccountClient;
import com.ibm.demo.config.properties.AppProperties;
import com.ibm.demo.config.properties.HttpClientProperties;
import com.ibm.demo.order.OrderClient;
import com.ibm.demo.product.ProductClient;

import lombok.RequiredArgsConstructor;

/**
 * 透過 Boot 4 HTTP Service Clients 自動註冊 {@code @HttpExchange} 介面為 bean。
 * {@code @ImportHttpServices} 將三個 client 歸入 "internal" group；底層 RestClient
 * （baseUrl / 自訂連線池 / 錯誤轉譯）由下方的 group configurer 統一設定。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({ AppProperties.class, HttpClientProperties.class })
@ImportHttpServices(group = "internal", types = { AccountClient.class, OrderClient.class, ProductClient.class })
public class RestClientConfig {

    private final AppProperties appProperties;
    private final HttpClientProperties httpClientProperties;

    // 自訂 ClientHttpRequestFactory：Apache HttpClient5 連線池 + 超時。
    // 連線池上限 / evict-idle / connection-request-timeout 等屬性無法以原生 group 設定表達，故保留手動配置。
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(httpClientProperties.getMaxTotal()); // 總連線池上限
        connectionManager.setDefaultMaxPerRoute(httpClientProperties.getDefaultMaxPerRoute()); // 每個單一服務的上限

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                // 關鍵：給予虛擬執行緒足夠的排隊時間，不要一拿不到連線就斷開
                .setDefaultRequestConfig(RequestConfig.custom()
                        // 從池中拿連線
                        .setConnectionRequestTimeout(
                                Timeout.of(httpClientProperties.getConnectionRequestTimeout()))
                        // 等待 API 回傳
                        .setResponseTimeout(Timeout.of(httpClientProperties.getResponseTimeout()))
                        .build())
                .evictIdleConnections(TimeValue.of(httpClientProperties.getEvictIdleConnectionsPeriod()))
                .build();

        factory.setHttpClient(httpClient);
        return factory;
    }

    /**
     * 設定 "internal" group 中所有 client 共用的 RestClient：base URL、自訂連線池 factory、
     * 以及將 HTTP error 轉譯為領域例外的 status handler。
     * <p>
     * factory 在 lambda 外建立一次後共用，讓三個 client 共享同一個連線池（與重構前行為一致）。
     */
    @Bean
    RestClientHttpServiceGroupConfigurer internalHttpServiceGroupConfigurer(RestClientErrorHandler errorHandler) {
        ClientHttpRequestFactory requestFactory = clientHttpRequestFactory();
        return groups -> groups.forEachClient((group, clientBuilder) -> clientBuilder
                .baseUrl(appProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> errorHandler.handle(response)));
    }
}
