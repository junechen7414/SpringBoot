# Spring Boot 4 升級計畫

> **建立日期**: 2026-06-17  
> **目標版本**: Spring Boot 4.0.7 / Spring Framework 7.0.7+ / Jakarta EE 11  
> **當前版本**: Spring Boot 3.5.15 / Java 21 / Gradle 8.13  
> **預估工時**: 1～3 人日（視第三方依賴相容性而定）

---

## 目標版本矩陣

| 元件 | 當前版本 | 目標版本 | 備註 |
|------|---------|---------|------|
| Spring Boot | 3.5.15 | **4.0.7** | 最新穩定版（2026-06-10 發布；4.1.x 線已出現，本計畫先走 4.0.x）|
| Spring Framework | 6.2.x | **7.0.7+** | Boot 4 內建 |
| Java | 21 | **21** | 不需變更 |
| Gradle | 8.13 | **8.14+** | Boot 4 要求 |
| Jakarta EE | 10 | **11** | 新 baseline |
| Hibernate | 6.x | **7.x** | JPA 3.2 |
| Jackson | 2.x | **3.x** | 🔴 重大變更 |

---

## Phase 0：前置準備

### 目標
確認升級前的基準狀態，建立安全網。

### 步驟

| # | 動作 | 命令/說明 |
|---|------|----------|
| 0.1 | 開新分支 | `git checkout -b feature/spring-boot-4-upgrade` |
| 0.2 | 確認現有測試全部通過 | `./gradlew test` |
| 0.3 | 保存依賴快照 | `./gradlew dependencies > before-upgrade.txt` |
| 0.4 | 升級 Gradle Wrapper | `./gradlew wrapper --gradle-version=8.14`（目前已是 8.13，僅需 minor patch；以官方 compatibility matrix 為準） |
| 0.5 | 確認無 javax 殘留 | `grep -r "javax\." .`（搜尋整個專案，包含 test/、config/、generated/） |
| 0.6 | 閱讀官方遷移指南 | [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) |

### 驗證標準
- [ ] `./gradlew test` 全綠
- [ ] `grep -r "javax\." .` 無結果（排除 .gradle/ 和 build/ 目錄）
- [ ] Gradle Wrapper 升級成功

---

## Phase 1：在 Boot 3.5 上先升級第三方依賴

### 目標
降低 Boot 4 升級時的變數，先確認第三方依賴在最新版本下仍然相容。

### 依賴升級清單

| 依賴 | 當前版本 | 目標版本 | 風險 | 備註 |
|------|---------|---------|------|------|
| Lombok | 1.18.36 | 1.18.42+ | 🟢 極低 | Java 21 annotation processor 修正 |
| Testcontainers (oracle-free) | 2.0.5 | 最新穩定版 | 🟡 中 | Oracle Free 容器啟動問題修正 |
| SpringDoc OpenAPI | 2.8.6 | Boot 4 相容版本 | 🔴 高 | 與 Spring Framework 7 綁定緊密 |
| Resilience4j | 2.3.0 (boot3) | boot4 artifact | 🔴 高 | artifact name 已分家 |
| OpenTelemetry | BOM 管理 | 交給 Boot 4 BOM | 🟡 中 | Boot 4 Observability 改動大 |
| Micrometer OTLP | BOM 管理 | 交給 Boot 4 BOM | 🟡 中 | 不要自行指定版本 |

### 注意事項
- SpringDoc 和 Resilience4j 可能無法在 Boot 3.5 上升級到 Boot 4 相容版本
- 如果遇到此情況，記錄下來，留到 Phase 2 一起處理
- 其他依賴（Lombok、Testcontainers）應該可以先升級

### 驗證標準
- [ ] `./gradlew compileJava` 無錯誤
- [ ] `./gradlew test` 全綠
- [ ] 記錄無法在 Boot 3.5 上升級的依賴

---

## Phase 2：升級 Spring Boot 4.0.6

### 目標
完成核心框架升級，解決編譯錯誤。

### 步驟

| # | 動作 | 說明 |
|---|------|------|
| 2.1 | 更新 `build.gradle` plugin 版本 | `id 'org.springframework.boot' version '4.0.7'` |
| 2.2 | 評估移除 `io.spring.dependency-management` | Boot 4 建議直接使用 Gradle BOM，用 `./gradlew dependencyInsight` 確認版本來源 |
| 2.3 | 更新 Resilience4j artifact | `resilience4j-spring-boot3` → `resilience4j-spring-boot4` |
| 2.4 | 更新 SpringDoc 版本 | 升級到支援 Boot 4 的版本 |
| 2.5 | 加入 properties-migrator | `runtimeOnly 'org.springframework.boot:spring-boot-properties-migrator'` |
| 2.6 | 執行編譯 | `./gradlew compileJava` |
| 2.7 | 逐一修復編譯錯誤 | 根據錯誤訊息修正 |

### build.gradle 變更範例

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.7'
    // 評估是否仍需要 dependency-management
}

dependencies {
    // 升級期間暫時加入 — 偵測已改名/移除的 configuration properties
    runtimeOnly 'org.springframework.boot:spring-boot-properties-migrator'
    
    // Resilience4j - 改用 boot4 artifact
    implementation 'io.github.resilience4j:resilience4j-spring-boot4'
    
    // SpringDoc - 升級到 Boot 4 相容版本
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:X.X.X'
    
    // Observability - 交給 BOM 管理，不指定版本
    implementation 'io.micrometer:micrometer-registry-otlp'
    implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
}
```

### 驗證標準
- [ ] `./gradlew compileJava` 無錯誤
- [ ] 應用程式可以啟動（觀察 properties-migrator 提示）

---

## Phase 2.5：Deprecation Cleanup

### 目標
清除所有 deprecation 警告。Boot 3 的 deprecated = Boot 4 的 removed API。

### 步驟

```bash
./gradlew build --warning-mode=all
```

### 驗證標準
- [ ] 0 deprecation warning
- [ ] `./gradlew compileJava -Werror` 通過（強制將警告視為錯誤）

---

## Phase 3：修復與適配

### 目標
修復因框架升級導致的 API 變化。

---

### 3.1 `@SQLRestriction` (Hibernate 7) 🔴

**檔案**: `src/main/java/com/ibm/demo/account/Account.java`

```java
@SQLRestriction("STATUS = 'Y' AND DELETED = false")
```

**檢查點**: 
- Hibernate 7 中此註解是否有 API 變化或重新命名
- **驗證 generated SQL 是否一致**：使用 `@DataJpaTest` 比對查詢結果

```java
// 驗證 SQL 是否仍正確套用
@DataJpaTest
class AccountRepositoryHibernate7Test {
    // 確認 where STATUS='Y' AND DELETED=false 仍正確生成
}
```

---

### 3.2 `ResponseEntityExceptionHandler` 方法簽名

**檔案**: `src/main/java/com/ibm/demo/GlobalExceptionHandler.java`

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request)
```

**檢查點**: Spring Framework 7 中此方法的參數是否有增減。

**偵測方式**: `./gradlew compileJava --stacktrace` — 方法簽名一變，編譯器會直接報錯。

---

### 3.3 Resilience4j 配置

**檔案**: `application.yml` 中的 `resilience4j.*` 配置

**檢查點**: 
- `resilience4j-spring-boot4` 的配置 key 是否有變化
- Exception package 是否有移動：`import io.github.resilience4j.*` 是否全部仍存在
- `BulkheadFullException`, `CallNotPermittedException`, `RequestNotPermitted` 的 package 確認

> **未來評估備註**: Spring Framework 7 已內建部分 Retry/Timeout/Concurrency Limit 能力。
> 本專案目前使用 CircuitBreaker + RateLimiter + Bulkhead + Metrics Integration，
> 暫時保留 Resilience4j 是合理的。未來可評估是否遷移到 Spring 原生方案。

---

### 3.4 RestClientConfig

**檔案**: `src/main/java/com/ibm/demo/config/RestClientConfig.java`

**檢查點**:
- `HttpServiceProxyFactory` API 是否有變化
- `RestClientAdapter` 是否仍然存在
- Boot 4 是否提供了更簡潔的 auto-configuration 可以取代手動配置

**策略**: **先確認現有配置能正常運作，再考慮簡化**。

---

### 3.5 Observability / Actuator

**檔案**: `docker-compose.yml`, `prometheus.yml`, `config.alloy`

**檢查點**:
- Actuator endpoints 路徑是否有變化
- Metrics 名稱是否有變化（影響 Grafana dashboard）
- OTLP exporter 配置是否有變化
- Boot 4 升級了 Micrometer 2.x，確認 metrics API 相容性

---

### 3.6 Jackson 3 適配 🔴

**影響範圍**: 所有 API Response 的 JSON 序列化

**Boot 4 的 Jackson 3 重大變更**:
- 套件名可能從 `com.fasterxml.jackson` 變為 `tools.jackson`
- 日期格式：預設輸出為 ISO-8601 字串（不再是 Numeric Timestamps）
- 欄位排序：可能預設改為字母排序
- `@JsonFormat`、`@JsonProperty` 行為可能有微調
- 序列化異常改為 Unchecked Exception

**需要檢查的檔案**:
- 所有 DTO / Response 類別中的 `@JsonFormat`、`@JsonProperty`
- `LocalDateTime` 序列化格式
- Enum 序列化方式
- `GlobalExceptionHandler` 中對 Jackson 異常的捕獲

**驗證方式**:
```java
// 比對升級前後的 JSON 輸出
@SpringBootTest
@AutoConfigureMockMvc
class JacksonSerializationTest {
    // 確認 API response JSON 格式一致
}
```

---

### 3.7 Configuration Properties Validation

**工具**: `spring-boot-properties-migrator`（Phase 2.5 已加入）

**步驟**:
1. 啟動應用程式
2. 觀察 console 中的 properties-migrator 提示訊息：`Property xxx has been renamed to yyy`
3. 逐一修正 `application.yml` 中被改名的 properties

**特別關注的配置區塊**:
- `spring.datasource.*`
- `management.*`（Actuator）
- `logging.*`
- `resilience4j.*`
- `spring.jpa.*`
- `spring.flyway.*`
- `server.*`

**完成後**: 移除 `spring-boot-properties-migrator` 依賴（僅升級期間使用）。

---

## Phase 4：全面測試

### 目標
確認升級後所有功能正常運作。

### 測試清單

| # | 測試類型 | 命令/動作 | 驗證重點 |
|---|---------|----------|---------|
| 4.1 | 編譯檢查 | `./gradlew compileJava --warning-mode=all` | 無隱藏警告 |
| 4.2 | Unit Tests | `./gradlew test --tests "*UnitTest*"` | Mockito + JUnit 5 |
| 4.3 | Integration Tests | `./gradlew test --tests "*IntegrationTest*"` | Testcontainers + Oracle |
| 4.4 | DataJpa Tests | `./gradlew test --tests "*DataJpaTest*"` | H2 + Flyway + Hibernate 7 SQL 驗證 |
| 4.5 | Flyway 驗證 | `./gradlew flywayValidate` | Oracle migration scripts（新版 parser 可能更嚴格） |
| 4.6 | 應用啟動 | `./gradlew bootRun` | 確認無啟動錯誤 |
| 4.7 | Actuator | `curl localhost:8787/actuator/health` | 健康檢查正常 |
| 4.8 | OpenAPI | `curl localhost:8787/swagger-ui.html` | Swagger UI 正常 |
| 4.9 | Docker Build | `docker build -t springboot-app .` | 容器化正常 |
| 4.10 | Resilience4j Metrics | 確認 Grafana dashboard | Metrics 路徑是否一致 |
| 4.11 | **JSON 序列化回歸測試** | MockMvc + JSON Assert | 確認 API response 格式未因 Jackson 3 改變 |

### 驗證標準
- [ ] `./gradlew test` 全綠
- [ ] 應用程式可正常啟動並回應請求
- [ ] Docker image 可正常建置和運行
- [ ] JSON response 格式與升級前一致

---

## Phase 5：清理與優化

### 目標
利用 Boot 4 新特性簡化代碼，清理技術債。

### 步驟

| # | 動作 | 說明 |
|---|------|------|
| 5.1 | 移除 `spring-boot-properties-migrator` | 升級完成，不再需要 |
| 5.2 | 簡化 RestClientConfig | 如果 Boot 4 支援 auto-proxy，移除手動 Factory 配置 |
| 5.3 | 移除 deprecated `BaseEntity` | 已標記 `@Deprecated(forRemoval = true)`，趁機清理 |
| 5.4 | 更新 Dockerfile | Gradle 版本對齊 |
| 5.5 | 依賴快照比對 | `./gradlew dependencies > after-upgrade.txt` + `diff` |
| 5.6 | 移除 `io.spring.dependency-management`（如適用） | 改用原生 Gradle BOM |
| 5.7 | 更新專案文件 | README、AGENTS.md 等 |

### 驗證標準
- [ ] 代碼更簡潔
- [ ] 無 deprecated 警告
- [ ] 文件已更新
- [ ] `./gradlew test` 仍然全綠

---

## 風險評估

| 風險等級 | 項目 | 影響範圍 | 緩解策略 |
|---------|------|---------|---------|
| 🔴 高 | **Jackson 3** | 所有 API JSON 輸出格式 | JSON 序列化回歸測試、比對升級前後輸出 |
| 🔴 高 | **Hibernate 7 / @SQLRestriction** | Entity 查詢邏輯 | DataJpaTest 驗證 generated SQL |
| 🔴 高 | **SpringDoc OpenAPI 相容性** | Swagger UI 無法使用 | 查找 Boot 4 相容版本，必要時暫時移除 |
| 🟡 中高 | Observability (OTLP/Micrometer 2.x) | 監控鏈路 | 交給 Boot BOM 管理版本 |
| 🟡 中高 | Resilience4j artifact 變更 | 限流/熔斷 | 改用 `resilience4j-spring-boot4` |
| 🟡 中高 | Flyway Oracle parser | Migration 驗證 | `flywayValidate` + 修正 SQL |
| 🟡 中 | Configuration Properties 改名 | 應用啟動失敗 | `spring-boot-properties-migrator` |
| 🟡 中 | `ResponseEntityExceptionHandler` 簽名 | 例外處理 | 編譯器會直接報錯 |
| 🟢 低 | Entity / Repository / Service | 業務邏輯 | 使用標準 JPA，風險極低 |
| 🟢 低 | Virtual Threads | 執行緒模型 | 配置方式不變 |
| 🟢 低 | Lombok | 編譯 | 升級到最新版即可 |
| 🟢 低 | Oracle JDBC | 資料庫連線 | ojdbc11 對 Boot 4 無特殊問題 |

---

## 不需要擔心的項目

以下項目**不適用於本專案**，無需處理：

- ❌ **Undertow 淘汰** — 本專案使用預設 Tomcat
- ❌ **javax → jakarta 遷移** — Boot 3 時已完成
- ❌ **JUnit 4 移除** — 本專案全用 JUnit 5
- ❌ **Java 版本升級** — 已經是 Java 21
- ❌ **private final 反射修改** — 使用 constructor injection + Mockito
- ❌ **Native Image** — 不在本次升級範圍（可作為未來方向）

---

## 未來方向（升級完成後）

| 方向 | 說明 |
|------|------|
| Resilience4j → Spring 原生 | Spring Framework 7 內建 Retry/Timeout/Concurrency Limit，未來可評估遷移 |
| RestClientConfig 簡化 | 利用 Boot 4 的 `@HttpExchange` auto-proxy 進一步簡化 |
| GraalVM Native Image | Boot 4 對 Native Image 支援更完整，可作為效能優化方向 |
| PostgreSQL 遷移 | 本次升級完成後的下一步 |
| Render 部署 | PostgreSQL 遷移完成後部署上線 |

---

## 參考資源

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Gradle Plugin Registry - Spring Boot 4.0.7](https://plugins.gradle.org/plugin/org.springframework.boot/4.0.7)
- [Spring Boot 4.0 Release Highlights](https://spring.io/projects/release-highlights)
- [Preparing for Spring Boot 4 and Spring Framework 7](https://foojay.io/today/preparing-for-spring-framework-7-and-spring-boot-4/)
- [Planning Spring Boot 4 Migration](https://www.openlogic.com/blog/planning-spring-boot-4-migration)

---

## 執行策略

```
分工模式：
- RAG AI（有網路查詢能力）：提供最新版本號、官方連結、breaking changes 確認
- Cline（有 codebase 存取能力）：基於實際代碼判斷影響範圍、執行修改、跑測試

每個 Phase 的流程：
1. 帶著 Phase 內容去跟 RAG AI 確認最新資訊
2. 回來讓 Cline 基於確認後的資訊執行修改
3. 跑測試驗證
4. 進入下一個 Phase
```

---

**最後更新**: 2026-06-18  
**維護者**: Bobby
