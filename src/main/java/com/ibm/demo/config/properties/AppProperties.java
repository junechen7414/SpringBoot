package com.ibm.demo.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
// @Setter is needed for @ConfigurationProperties to work, because Spring needs to set the properties
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    @NotNull(message = "Base URL must not be null")
    private String baseUrl;

    @Valid
    @NestedConfigurationProperty
    private Auth auth = new Auth();

    /**
     * HTTP Basic 認證帳密：
     * - api：一般 API 呼叫端使用。
     * - internal：供內部 *Client loopback 自呼叫帶入，讓自呼叫能通過自己的 Security filter chain
     *   （見 RestClientConfig）。
     * 帳密以 env 覆寫（見 application.yml 的 app.auth.*）。
     */
    @Getter
    @Setter
    public static class Auth {
        @NotBlank
        private String apiUsername;
        @NotBlank
        private String apiPassword;
        @NotBlank
        private String internalUsername;
        @NotBlank
        private String internalPassword;
    }
}