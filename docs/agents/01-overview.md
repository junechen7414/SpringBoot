## 專案概述

基於 **Spring Boot 4.1.1** 與 **Java 25**（Virtual Threads）的企業級微服務範例，資料存在 **Oracle Database**，本地開發與監控環境以 **Podman Compose** 起。

### 核心技術棧

- **框架**: Spring Boot 4.1.1 (WebMVC, RestClient, Data JPA, Validation, AspectJ, Actuator)
- **資料庫**: Oracle（正式）／H2（測試與文件生成）；遷移用 Flyway
- **容錯**: Resilience4j (Bulkhead, Circuit Breaker, Rate Limiter)
- **HTTP 客戶端**: RestClient（取代 WebClient）；`@ImportHttpServices` 為 `@HttpExchange` interface 註冊 runtime proxy bean，`RestClientHttpServiceGroupConfigurer` 統一設定傳輸
- **API 文件**: SpringDoc OpenAPI 3
- **監控**: Grafana Alloy + Prometheus（指標）+ Tempo（追蹤）+ Grafana
- **測試**: JUnit 5, Mockito, Testcontainers
- **CI/CD**: GitHub Actions，映像推到 GHCR

### 架構特色

1. **分層架構**: Controller → Service → Repository → Entity；跨模組呼叫走 `*Client`（細節見 06）
2. **軟刪除與審計**: `@Embedded` 組合 `SoftDeleteMetadata` / `AuditMetadata`
3. **樂觀鎖**: `@Version`
4. **全域異常處理**: `@RestControllerAdvice`，對外 RFC 9457 `application/problem+json`
5. **併發控制**: Resilience4j Bulkhead fail-fast
6. **統一分頁**: 列表一律回 `PageResponse<T>`，沒有非分頁列表端點
7. **環境隔離**: Spring Profiles（dev, integration-test, e2e, openapi）
8. **監控鏈路**: 指標 App → Alloy → Prometheus；追蹤 App → Alloy → Tempo；兩條只共用 App → Alloy

### 業務領域

- **Account**: 帳戶 CRUD 與狀態管理
- **Product**: 商品資訊與庫存
- **Order**: 訂單建立、更新與明細，整合 account 與 product

新增 domain 時照 `new-domain-scaffold` skill 的清單走。

### 語言與工具偏好

- **回應語言**: 繁體中文，技術術語保留英文；不使用簡體中文。
- **容器**: 一律 `podman` / `podman compose`。
- **前端套件管理**: 一律 `pnpm`。

### Shell 偵測

執行 CLI 前先從環境資訊判斷 shell，再用對應語法：

| | PowerShell | CMD | Git Bash |
|---|---|---|---|
| 串接 | `;` | `&&` | `&&` / `;` |
| Gradle wrapper | `./gradlew` | `gradlew` | `./gradlew` |
| 環境變數 | `$env:VAR` | `%VAR%` | `$VAR` |

PowerShell 的 `-D` 參數要加引號：`./gradlew test "-Djunit.platform.exclude.tags=SanityTest"`。
