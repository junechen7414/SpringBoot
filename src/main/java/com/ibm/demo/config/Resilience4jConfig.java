package com.ibm.demo.config;

import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j 配置類
 * 
 * 用於配置限流、隔離艙等彈性機制
 * 實際配置參數在 application.yml 中定義
 * 
 * 使用方式：
 * - @Bulkhead(name = "database") - 控制並發數量
 * - @RateLimiter(name = "database") - 控制請求速率
 */
@Configuration
public class Resilience4jConfig {
    // 配置由 application.yml 和 Resilience4j 自動配置處理
    // 此類作為配置的標記和文檔說明
}
