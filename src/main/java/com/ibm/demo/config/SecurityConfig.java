package com.ibm.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import com.ibm.demo.config.properties.AppProperties;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security 設定：inbound HTTP Basic 認證。
 *
 * <b>定位：這是佔位方案。</b>只夠服務對服務與教學用 —— 兩個使用者都是 in-memory 機器帳號，
 * 且沒有任何方法級授權（authZ）。正式面向使用者的系統不該自己承擔使用者存放、密碼雜湊與
 * token 簽發，而應只當 OAuth2 Resource Server、把這些交給外部 IdP；遷移步驟見
 * {@code docs/security-external-idp-migration.md}。
 *
 * Security 是 servlet filter chain，只攔截「進入本應用的 HTTP 請求」，因此：
 * - Oracle/Hikari 連線、往外推的 OTLP metrics、容器間網路都不經過此 chain，不受影響。
 * - actuator health 與 Swagger 文件端點是 inbound HTTP，預設會被擋，故在下方明確 permitAll。
 *   （health 是給 Dockerfile HEALTHCHECK 的 wget 探針用——它也走這條 filter chain，不放行就會 401。）
 *
 * 監控走 OTLP push（app → Alloy），沒有需要放行的 actuator scrape 端點。
 *
 * 使用者以 in-memory 定義（帳密走 env），不建立 DB 使用者表 —— DB schema 零改動。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AppProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties appProperties;

    /**
     * 一般 profile 的 filter chain：純 REST、無狀態、HTTP Basic。放行健康檢查與文件端點，其餘一律需認證。
     * 只放行 /actuator/health（Dockerfile 探針）；其餘 actuator 端點落入 anyRequest().authenticated()。
     */
    @Bean
    @Profile("!openapi")
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 防的是「瀏覽器帶著 ambient credential（cookie）被誘導發出跨站請求」。這裡三個前提都不成立：
                // 沒有 cookie session（下一行就是 STATELESS）、憑證由呼叫端每次自己帶 Basic header，
                // 而且 Spring 預設的 CSRF token repository 就是存在 HttpSession 裡的，與 STATELESS 直接矛盾。
                // 換言之這是 stateless REST API 的標準做法，不是「關掉防護」。
                .csrf(csrf -> csrf.disable())
                // STATELESS：不建 session、不發 JSESSIONID。預設 IF_REQUIRED 會為每個認證請求建 session；
                // 但這裡的 *Client 是 loopback 自呼叫、每次都自帶 Basic 憑證，不需要「記住登入」。若留著 session，
                // 每通自呼叫要嘛各建一個 session 造成爆量堆積，要嘛共用一份 cookie 產生隱藏的黏性狀態——
                // 在高併發自呼叫下純屬負擔。STATELESS 讓模型對齊「機器對機器、每次帶憑證」的真實語義。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 健康檢查：Dockerfile HEALTHCHECK 探針（wget /actuator/health）。這是唯一硬需求的放行。
                        .requestMatchers("/actuator/health/**").permitAll()
                        // 文件：讓 Swagger UI / OpenAPI JSON 執行時免登入即可瀏覽
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /**
     * openapi profile 專用：全放行。generateOpenApiDocs 以 bootRun 啟動並抓取 /v3/api-docs，
     * 若被擋則文件（swagger.json）無法產生。
     */
    @Bean
    @Profile("openapi")
    SecurityFilterChain openApiFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * in-memory 使用者：
     * - api：一般 API 呼叫端。
     * - internal：供內部 *Client loopback 自呼叫使用（見 RestClientConfig）。
     *
     * <b>密碼刻意不做雜湊</b>，以 {@code {noop}} 前綴交給 Spring 預設的 DelegatingPasswordEncoder
     * 逐字比對。理由是雜湊在這裡對不上任何威脅模型，只剩成本：
     * <ul>
     * <li>這兩個都是機器帳號，密碼本來就從 env 明文注入到同一個 process。先 encode 再 matches
     * 並沒有多保護什麼 —— 沒有會外洩的密碼資料庫。</li>
     * <li>STATELESS + HTTP Basic 表示<b>每個請求都要重新認證一次</b>，Spring 沒有預設的憑證驗證快取；
     * 而 *Client 是 loopback 自呼叫，一筆 order 會展開成多次自呼叫，每次都得再付一次密碼驗證。
     * BCrypt（strength 10）單次數十毫秒的成本乘上這個放大倍數，會直接吃掉 application.yml 裡
     * 那整套 Resilience4j 併發/QPS 調校的預算。</li>
     * </ul>
     * 真正的使用者密碼一律該走外部 IdP（雜湊、輪替、鎖定策略都在那邊），本應用只驗 token；
     * 見 {@code docs/security-external-idp-migration.md}。
     */
    @Bean
    UserDetailsService userDetailsService() {
        AppProperties.Auth auth = appProperties.getAuth();
        // roles 目前沒有任何授權規則在用（授權只有 anyRequest().authenticated()，全專案無 @PreAuthorize /
        // hasRole）。保留它們是因為這是未來映射 IdP role claim 成 authority 的接縫 —— 遷移時只要把
        // JwtAuthenticationConverter 的輸出對上這兩個名字，方法級授權就能原地長出來。
        UserDetails apiUser = User.withUsername(auth.getApiUsername())
                .password("{noop}" + auth.getApiPassword())
                .roles("API")
                .build();
        UserDetails internalUser = User.withUsername(auth.getInternalUsername())
                .password("{noop}" + auth.getInternalPassword())
                .roles("INTERNAL")
                .build();
        return new InMemoryUserDetailsManager(apiUser, internalUser);
    }
}
