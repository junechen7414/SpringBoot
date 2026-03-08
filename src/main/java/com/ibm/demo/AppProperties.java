package com.ibm.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
}