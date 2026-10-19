package com.ibm.demo.downstream;

import java.util.Set;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.demo.GlobalExceptionHandler;
import com.ibm.demo.RestClientErrorHandler;
import com.ibm.demo.config.RestClientConfig;
import com.ibm.demo.config.SecurityConfig;
import com.ibm.demo.product.ProductClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;

/**
 * 下游錯誤轉譯測試專用的最小 Spring 設定：只拉進「從 {@code *Client} 往外打、錯誤回來、轉成對外回應」
 * 這條路上真正會經過的零件，不含 JPA／Flyway／Oracle。
 *
 * <p>與 {@code contract.ContractTestApplication} 同樣刻意不用 {@code @ComponentScan}，並放在獨立 package：
 * 測試往上找最近的 {@code @SpringBootConfiguration}，放在這裡才不會被找到 main 的 {@code DemoApplication}
 * 而把整個應用拉起來。
 *
 * <p>真正接上的是：正式的 {@link RestClientConfig}（連線池、Basic 憑證、status handler 接線）、
 * {@link RestClientErrorHandler}、{@link GlobalExceptionHandler}、{@link SecurityConfig}，以及
 * resilience4j 的 CircuitBreaker（讀的是正式的 {@code application.yml}，所以 {@code ignore-exceptions}
 * 一改，這裡的計數測試就會紅）。
 */
@SpringBootConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ImportAutoConfiguration(CircuitBreakerAutoConfiguration.class)
@Import({
        RestClientConfig.class,
        RestClientErrorHandler.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        DownstreamTestApplication.ProbeController.class,
        DownstreamTestApplication.ProbeService.class })
public class DownstreamTestApplication {

    /**
     * 掛的 breaker 名稱沿用正式的 {@code ProductService}：與正式 service 吃同一份 yml 設定，
     * 測的才是「正式設定下的分類結果」，而不是測試自己湊的一份。
     */
    public static final String BREAKER_NAME = "ProductService";

    @Service
    @CircuitBreaker(name = BREAKER_NAME)
    public static class ProbeService {

        private final ProductClient productClient;

        public ProbeService(ProductClient productClient) {
            this.productClient = productClient;
        }

        public int fetch() {
            return productClient.getProductDetails(Set.of(1)).size();
        }
    }

    @RestController
    public static class ProbeController {

        private final ProbeService probeService;

        public ProbeController(ProbeService probeService) {
            this.probeService = probeService;
        }

        @GetMapping("/probe/downstream/product")
        public Fetched fetch() {
            return new Fetched(probeService.fetch());
        }

        public record Fetched(int count) {
        }
    }
}
