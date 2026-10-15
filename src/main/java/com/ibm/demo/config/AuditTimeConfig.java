package com.ibm.demo.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 稽核時間的唯一來源。
 *
 * <p>{@code CREATED_AT}／{@code UPDATED_AT} 由 Spring Data auditing 寫入，{@code DELETED_AT} 與 bulk
 * UPDATE 順手更新的 {@code UPDATED_AT} 則由 service 以 {@code LocalDateTime.now(clock)} 傳進 repository。
 * 兩條路共用這一個 {@link Clock}，同一筆資料的稽核時間才不會一部分來自 JVM、一部分來自 DB 的
 * {@code CURRENT_TIMESTAMP} —— 兩邊時區一不同（例如 Oracle 容器是 UTC、本機是台北時間）就差好幾小時。
 *
 * <p>{@code @EnableJpaAuditing} 放在這裡而不是 {@code DemoApplication}：auditing 依賴下面的
 * {@code auditingDateTimeProvider}，兩者放在同一個設定類別，載入其一就一定有另一個。
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class AuditTimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
