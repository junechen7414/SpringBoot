package com.ibm.demo.config.properties;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
// @Setter is needed for @ConfigurationProperties to work, because Spring needs to set the properties
@Setter
@Validated
@ConfigurationProperties(prefix = "http.client")
public class HttpClientProperties {

    @Positive
    @NotNull(message = "Max total connections must be greater than 0")
    private int maxTotal;

    @Positive
    @NotNull(message = "Default max per route must be greater than 0")
    private int defaultMaxPerRoute;

    @DurationMin(seconds = 1)
    @NotNull(message = "Connection request timeout must be set")
    private Duration connectionRequestTimeout;

    @DurationMin(seconds = 1)
    @NotNull(message = "Response timeout must be set")
    private Duration responseTimeout;

    @DurationMin(seconds = 1)
    @NotNull(message = "Evict idle connections period must be set")
    private Duration evictIdleConnectionsPeriod;
}