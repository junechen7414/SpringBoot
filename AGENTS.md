# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## 專案概述

這是一個基於 **Spring Boot 3.5.14** 與 **Java 21** 的企業級微服務應用程式，展示現代化的後端開發實踐。專案採用 **Virtual Threads** 提升併發效能，整合 **Oracle Database** 作為主要資料存儲，並透過 **Docker Compose** 實現完整的本地開發與監控環境。

### 核心技術棧

- **框架**: Spring Boot 3.5.14 (Web, Data JPA, Validation, AOP, Actuator)
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
6. **環境隔離**: 透過 Spring Profiles 管理多環境配置 (dev, unit-test, integration-test, e2e, openapi)
7. **監控鏈路**: App (OTLP) → Alloy → Prometheus → Grafana

### 業務領域

專案實作三個核心領域模組：
- **Account (帳戶管理)**: 使用者帳戶的 CRUD 與狀態管理
- **Product (商品管理)**: 商品資訊與庫存控制
- **Order (訂單管理)**: 訂單建立、更新與明細管理，整合帳戶與商品服務

## 建置與執行

### 前置需求

- **Java 21** (建議使用 Eclipse Temurin)
- **Podman** 或 Docker (用於容器管理)
- **Gradle 8.6+** (專案已包含 Gradle Wrapper)

### 本地開發環境啟動

#### 1. 啟動完整環境 (應用 + 資料庫 + 監控)

```bash
# 使用 Podman (推薦)
podman compose up -d

# 或使用 Docker
docker compose up -d
```

此命令會啟動：
- **Spring Boot App** (http://localhost:8787)
- **Oracle Database** (localhost:1521)
- **Grafana Alloy** (收集指標)
- **Prometheus** (http://localhost:9090)
- **Grafana** (http://localhost:3000)

#### 2. 僅啟動資料庫 (用於 IDE 內執行應用)

```bash
podman compose up oracle-db -d
```

然後在 IDE 中執行 `DemoApplication.java`，應用會連接到容器內的 Oracle DB。

#### 3. 環境變數配置

建立 `.env` 檔案於專案根目錄：

```env
ORACLE_DEV_USERNAME=your_username
ORACLE_DEV_PASSWORD=your_password
```

### 測試執行

#### 單元測試 (快速，使用 Mockito)

```bash
./gradlew test -Djunit.platform.exclude.tags=SanityTest
```

#### 整合測試 (使用 Testcontainers + Oracle)

```bash
./gradlew test --tests "*IntegrationTest"
```

**注意**: 整合測試會自動啟動 Oracle 容器，首次執行需下載映像檔。

### API 文件生成

```bash
./gradlew generateOpenApiDocs
```

產出檔案位於 `build/docs/swagger.json`，可匯入 Postman 或 Bruno 使用。

### 建置 Docker 映像檔

```bash
# 本地建置
podman build -t spring-boot-app:latest .

# 或透過 Compose 建置
podman compose build app
```

**多階段建置說明**:
- **第一階段**: 使用 `gradle:8.6-jdk21` 編譯並打包 JAR (跳過測試)
- **第二階段**: 使用 `eclipse-temurin:21-jre-alpine` 執行，最終映像檔僅包含 JRE 與應用程式

## 開發規範

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

### Git 工作流程

#### 分支命名規範

```
<類別>/<任務簡述>

範例:
feature/add-payment-module
bugfix/fix-order-calculation
hotfix/security-patch
refactor/optimize-query-performance
```

#### Commit 訊息規範 (Conventional Commits)

```
<type>(<scope>): <subject>

type: feat, fix, docs, style, refactor, test, chore
scope: 可選，如 account, order, product
subject: 使用祈使句，如 "add" 而非 "added"

範例:
feat(order): add bulk order creation endpoint
fix(product): resolve stock deduction race condition
docs: update API documentation for account module
refactor(service): replace WebClient with RestClient
```

### 程式碼設計原則

1. **可讀性優先**: 即使程式碼簡短或執行快速，若難以理解則不採用
2. **現代化語法**: 優先使用 Java 21 新特性（如 Virtual Threads, Pattern Matching）
3. **實務導向**: 理論正確但實務不適用的方案應避免

### 架構層次規範

#### Controller 層
- 僅負責請求映射與參數驗證 (`@Valid`)
- 不包含業務邏輯
- 使用 `ResponseEntity<T>` 明確控制 HTTP 回應

#### Service 層
- 核心業務邏輯所在
- 使用 `@Transactional` 管理事務
- 查詢方法標註 `@Transactional(readOnly = true)` 提升效能
- 拋出自定義業務異常（繼承 `BusinessException`）

#### Repository 層
- 繼承 `JpaRepository` 或 `SoftDeleteRepository`
- 優先使用方法名衍生查詢
- 複雜查詢使用 `@Query` (JPQL 優先於 Native SQL)

#### Entity 層
- 繼承 `BaseEntity` 獲得審計欄位與軟刪除支援
- 使用 `@SuperBuilder` 支援建構者模式
- 關聯關係標註 `@ToString.Exclude` 避免循環引用

### 測試策略

#### 單元測試 (Unit Test)
- 使用 Mockito 模擬依賴
- 遵循 AAA 模式 (Arrange, Act, Assert)
- 測試類別標註 `@ExtendWith(MockitoExtension.class)`

#### 整合測試 (Integration Test)
- 繼承 `BaseIntegrationTest` 自動啟動 Testcontainers
- 使用真實資料庫驗證 SQL 語法與業務流程
- 標註 `@ActiveProfiles("integration-test")`

#### 測試資料初始化
- **靜態資料**: 使用 SQL 檔案 (`data.sql`)
- **動態資料**: 使用 `CommandLineRunner` 搭配 `@Profile("dev")`
- **E2E 驗證**: 透過 REST API 建立測試資料

### 併發控制與容錯

#### Resilience4j 配置
- **Bulkhead**: 限制併發數，`max-wait-duration: 0ms` 實現 fail-fast
- **Circuit Breaker**: 故障率達 50% 觸發熔斷，等待 30 秒後進入半開狀態
- **Rate Limiter**: 每秒限制 100 個請求

#### 使用方式
```java
@Service
public class ProductService {
    @CircuitBreaker(name = "productService", fallbackMethod = "fallbackMethod")
    @Bulkhead(name = "productService")
    public Product getProduct(Long id) {
        // 業務邏輯
    }
}
```

### 文檔管理

所有專案文檔必須放置於 `docs/` 目錄，命名遵循 kebab-case：
- 指南類: `*-guide.md`
- 計劃類: `*-plan.md`
- 說明類: `*-explanation.md`
- 流程圖: `*-workflow.md` 或 `*-diagram.md`

### 環境配置管理

#### Profile 階層與覆寫規則
優先級（高 → 低）：
1. 環境變數 / 系統屬性
2. `application-{profile}.yml`
3. `application.yml`

#### 敏感資訊處理
- 使用 `.env` 檔案管理本地開發密碼
- 生產環境透過環境變數注入 (`${DB_PASSWORD:default}`)
- 禁止將密碼提交至 Git

### CI/CD 流程

#### GitHub Actions Workflow
1. **單元測試**: 執行 `./gradlew test` 作為 Quality Gate
2. **Docker 建置**: 多階段建置，僅在容器內執行 `bootJar`（跳過測試）
3. **映像檔推送**: 推送至 GitHub Container Registry (GHCR)
4. **文件生成**: 產生 OpenAPI 文件並推送至 Playwright 專案
5. **觸發 E2E**: 透過 `repository_dispatch` 通知 E2E 測試專案

#### 快取策略
- Gradle 依賴快取: `actions/setup-java` 的 `cache: gradle`
- Docker Layer 快取: `type=gha,scope=${{ github.ref_name }}`

### 監控與可觀測性

#### 指標收集鏈路
```
Spring Boot App (OTLP) → Grafana Alloy → Prometheus → Grafana
```

#### 關鍵端點
- **健康檢查**: `/actuator/health`
- **指標**: `/actuator/metrics`
- **Prometheus**: `/actuator/prometheus`

#### Resilience4j 監控指標
- `resilience4j.circuitbreaker.state`: 熔斷器狀態
- `resilience4j.bulkhead.available.concurrent.calls`: 可用許可數
- `resilience4j.circuitbreaker.failure.rate`: 失敗率

### 常見問題與最佳實踐

#### 為何使用 RestClient 而非 WebClient？
- **同步設計**: 搭配 Virtual Threads，阻塞變得廉價
- **可讀性**: 避免 WebClient 的非同步傳染性與複雜語法
- **維護性**: 團隊成員更容易理解與除錯

#### 為何使用 Resilience4j 取代自定義 Semaphore？
- **標準化**: Spring 生態系推薦方案，社群支援完善
- **監控整合**: 自動整合 Micrometer，無需手動開發
- **擴展性**: 可輕鬆堆疊熔斷、重試等功能

#### 為何整合測試使用 Testcontainers？
- **環境一致性**: 使用與生產環境相同的 Oracle 映像檔
- **完整驗證**: 支援所有 SQL 語法、Stored Procedures
- **CI/CD 友善**: 無需預先安裝資料庫，容器自動管理生命週期

#### 如何處理樂觀鎖衝突？
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiErrorResponse("資料已被其他使用者修改，請重新整理後再試"));
    }
}
```

## 參考資源

- **專案筆記**: `筆記.md` - 詳細的技術筆記與實作細節
- **文檔目錄**: `docs/` - 各類指南與計劃文件
- **全域指令**: `.github/instructions/Global.instructions.md` - 開發規範與偏好設定
- **Docker Compose**: `docker-compose.yml` - 完整的本地環境配置
- **CI/CD**: `.github/workflows/image-publish.yml` - 自動化流程定義

---

**最後更新**: 2026-05-25  
**維護者**: Bobby  
