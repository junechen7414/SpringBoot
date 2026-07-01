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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import com.ibm.demo.config.properties.AppProperties;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security 設定：inbound HTTP Basic 認證。
 *
 * Security 是 servlet filter chain，只攔截「進入本應用的 HTTP 請求」，因此：
 * - Oracle/Hikari 連線、往外推的 OTLP metrics、容器間網路都不經過此 chain，不受影響。
 * - actuator health/info 與 Swagger 文件端點是 inbound HTTP，預設會被擋，故在下方明確 permitAll。
 *
 * 監控走 OTLP push（app → Alloy），並無 /actuator/prometheus scrape 端點；仍一併放行該路徑
 * 屬防禦性設定（未來若加 prometheus registry 也不會被擋）。
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
     * 一般 profile 的 filter chain：純 REST、無狀態、HTTP Basic。放行監控與文件端點，其餘一律需認證。
     * actuator 以路徑比對（預設 base path /actuator）放行 health/info/prometheus；
     * metrics 等其餘 actuator 端點落入 anyRequest().authenticated()。
     */
    @Bean
    @Profile("!openapi")
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 監控：放行健康檢查（prometheus 路徑防禦性放行，目前走 OTLP push 無此端點）
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        // 文件：Swagger UI 與 OpenAPI JSON
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

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * in-memory 使用者：
     * - api：一般 API 呼叫端。
     * - internal：供內部 *Client loopback 自呼叫使用（見 RestClientConfig）。
     */
    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        AppProperties.Auth auth = appProperties.getAuth();
        UserDetails apiUser = User.withUsername(auth.getApiUsername())
                .password(passwordEncoder.encode(auth.getApiPassword()))
                .roles("API")
                .build();
        UserDetails internalUser = User.withUsername(auth.getInternalUsername())
                .password(passwordEncoder.encode(auth.getInternalPassword()))
                .roles("INTERNAL")
                .build();
        return new InMemoryUserDetailsManager(apiUser, internalUser);
    }
}
