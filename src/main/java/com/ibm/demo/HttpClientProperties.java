package com.ibm.demo;

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

    @Positive
    @NotNull(message = "Connection request timeout must be greater than 0")
    private int connectionRequestTimeout; // seconds

    @Positive
    @NotNull(message = "Response timeout must be greater than 0")
    private int responseTimeout; // seconds

    @Positive
    @NotNull(message = "Evict idle connections period must be greater than 0")
    private int evictIdleConnectionsPeriod; // seconds
}