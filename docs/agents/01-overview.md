## 專案概述

這是一個基於 **Spring Boot 4.0.7** 與 **Java 21** 的企業級微服務應用程式，展示現代化的後端開發實踐。專案採用 **Virtual Threads** 提升併發效能，整合 **Oracle Database** 作為主要資料存儲，並透過 **Docker Compose** 實現完整的本地開發與監控環境。

### 核心技術棧

- **框架**: Spring Boot 4.0.7 (WebMVC, RestClient, Data JPA, Validation, AspectJ, Actuator)
- **執行環境**: Java 21 with Virtual Threads
- **資料庫**: Oracle Database (生產) / H2 (測試與文件生成)
- **資料遷移**: Flyway
- **容錯處理**: Resilience4j (Bulkhead, Circuit Breaker, Rate Limiter)
- **HTTP 客戶端**: RestClient (取代 WebClient)
- **API 文件**: SpringDoc OpenAPI 3
- **監控系統**: Grafana Alloy + Prometheus + Grafana
- **測試框架**: JUnit 5, Mockito, Testcontainers
- **容器化**: Docker (多階段建置) / Podman
- **CI/CD**: GitHub Actions

### 架構特色

1. **分層架構**: Client → Controller → Service → Repository → Entity
2. **軟刪除機制**: 透過 `BaseEntity` 實現統一的軟刪除與審計欄位
3. **樂觀鎖**: 使用 `@Version` 防止併發更新衝突
4. **全域異常處理**: `@RestControllerAdvice` 統一攔截並格式化錯誤回應
5. **併發控制**: Resilience4j Bulkhead 取代自定義 Semaphore，實現 fail-fast 資源保護
6. **統一分頁**: 所有列表查詢使用 `PageResponse<T>` 封裝分頁回應，移除非分頁列表端點
7. **環境隔離**: 透過 Spring Profiles 管理多環境配置 (dev, unit-test, integration-test, e2e, openapi)
8. **監控鏈路**: App (OTLP) → Alloy → Prometheus → Grafana

### 業務領域

專案實作三個核心領域模組：
- **Account (帳戶管理)**: 使用者帳戶的 CRUD 與狀態管理
- **Product (商品管理)**: 商品資訊與庫存控制
- **Order (訂單管理)**: 訂單建立、更新與明細管理，整合帳戶與商品服務

### 語言與工具偏好

- **回應語言**: 繁體中文（優先），英文（技術術語）
- **容器工具**: 一律使用 `podman`，禁用 `docker` 指令
- **套件管理**: 前端專案使用 `pnpm`，禁用 `npm` 或 `yarn`

### Shell 指令執行規範

執行 CLI 指令前，必須先偵測當前使用的 Shell 環境（PowerShell 或 CMD），並根據環境調整指令語法：

| 差異項目 | PowerShell (`pwsh`) | CMD (`cmd.exe`) |
|---------|-------------------|-----------------|
| 指令串接 | 使用 `;` 分隔 | 使用 `&&` 分隔 |
| 執行當前目錄腳本 | `./gradlew` | `gradlew` (或 `.\gradlew`) |
| 環境變數引用 | `$env:VAR_NAME` | `%VAR_NAME%` |
| 路徑分隔 | 支援 `/` 和 `\` | 建議使用 `\` |

**範例對照：**
- PowerShell：`cd src; ./gradlew test`
- CMD：`cd src && gradlew test`

**重要原則：**
- 不可假設預設 Shell，須透過環境資訊判斷
- 指令語法必須與當前 Shell 相容，避免跨 Shell 語法混用
