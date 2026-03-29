package com.ibm.demo.config.properties;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.database.concurrency")
public class DatabaseConcurrencyProperties {
    /**
     * 各個資源的自定義限制。Key 為資源名稱（annotation 的 value），Value 為限制數量。
     */
    private Map<String, Integer> limits = new HashMap<>();

    /**
     * 全域預設限制，若 limits 找不到對應資源則回歸此設定
     */
    private int defaultLimit = 10;
}