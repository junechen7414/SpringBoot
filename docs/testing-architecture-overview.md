# 測試架構全貌說明

## 🏗️ 整體測試架構圖

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SpringBoot Repository (本專案)                      │
│                                                                     │
│  ┌─────────────────────┐   ┌──────────────────────────────────────┐ │
│  │  Layer 1: Unit Test │   │  Layer 2: Integration Test           │ │
│  │  (純 Mockito)        │   │  (Testcontainers + Oracle)           │ │
│  │                     │   │                                      │ │
│  │  • OrderServiceTest │   │  • OptimisticLockingIntegrationTest  │ │
│  │  • AccountServiceTest│  │  • DemoApplicationTests (SanityTest) │ │
│  │  • ProductServiceTest│  │                                      │ │
│  │  • OrderTransactional│  │  Base: BaseIntegrationTest           │ │
│  │    ServiceTest       │   │  Profile: integration-test           │ │
│  │  • GlobalException   │   │  DB: gvenzl/oracle-free:slim-faststart│
│  │    HandlerTest       │   │  (Testcontainers 自動管理)             │ │
│  │                     │   │                                      │ │
│  │  Tag: @UnitTest     │   │  Tag: @IntegrationTest               │ │
│  │  不需 Spring Context │   │  需要 @SpringBootTest                 │ │
│  │  不需資料庫          │   │  Flyway 自動 migrate                  │ │
│  └─────────────────────┘   └──────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    Playwright Repository (另一個專案)                   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Layer 3: E2E Test (Playwright + Docker Compose)              │   │
│  │                                                              │   │
│  │  docker-compose.test.yml 啟動：                                │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │   │
│  │  │ spring-boot  │  │  oracle-db   │  │   alloy      │       │   │
│  │  │ app (e2e)    │──│  (真實Oracle) │  │  (監控)      │       │   │
│  │  │ Port: 8787   │  │  Port: 1521  │  │  Port: 4318  │       │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘       │   │
│  │                                                              │   │
│  │  Profile: e2e                                                │   │
│  │  環境變數: ORACLE_TEST_USERNAME / ORACLE_TEST_PASSWORD         │   │
│  │  GitHub Actions 自動觸發 (repository_dispatch)                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 📋 Layer 1: Unit Test（單元測試）

| 項目 | 說明 |
|------|------|
| **位置** | `src/test/java/com/ibm/demo/` 各 Service 目錄 |
| **框架** | JUnit 5 + Mockito |
| **Tag** | `@Tag("UnitTest")` |
| **Profile** | 無（不啟動 Spring Context） |
| **資料庫** | 不需要 |
| **特色** | 純 Mock 測試，速度極快 |

**測試類別：**
- `OrderServiceTest` - 訂單服務邏輯（建立/更新/刪除，含補償機制）
- `AccountServiceTest` - 帳戶服務邏輯
- `ProductServiceTest` - 商品服務邏輯
- `OrderTransactionalServiceTest` - 訂單交易層邏輯
- `GlobalExceptionHandlerTest` - 全域例外處理器（直接 new，不用 Mock 框架）

---

## 📋 Layer 2: Integration Test（整合測試）

| 項目 | 說明 |
|------|------|
| **位置** | `src/test/java/com/ibm/demo/` |
| **框架** | JUnit 5 + Spring Boot Test + **Testcontainers** |
| **Tag** | `@Tag("IntegrationTest")`（DemoApplicationTests 額外有 `@Tag("SanityTest")`） |
| **Profile** | `@ActiveProfiles("integration-test")` |
| **資料庫** | `gvenzl/oracle-free:slim-faststart`（Testcontainers 自動啟動/銷毀） |
| **DDL** | `ddl-auto: validate` + Flyway migration |
| **Base Class** | `BaseIntegrationTest`（提供 `@Container` + `@ServiceConnection`） |

**測試類別：**
- `OptimisticLockingIntegrationTest` - 樂觀鎖機制驗證（JPA 標準 + 自定義 @Query）
- `DemoApplicationTests` - Sanity Test（Context 載入驗證）

**Testcontainers 機制：**
- 使用 `@ServiceConnection` 自動配置 DataSource 連線
- 容器生命週期由 JUnit 管理，測試結束自動銷毀
- 不需要手動啟動 Docker Compose

---

## 📋 Layer 3: E2E Test（端對端測試 - Playwright Repo）

| 項目 | 說明 |
|------|------|
| **位置** | 另一個 Playwright Repository |
| **框架** | Playwright (Node.js) |
| **Profile** | `e2e`（透過 `SPRING_PROFILES_ACTIVE=e2e` 設定） |
| **資料庫** | Oracle Free（Docker Compose 啟動） |
| **配置檔** | 本專案的 `src/test/resources/application-e2e.yml` |
| **環境變數** | `ORACLE_TEST_USERNAME` / `ORACLE_TEST_PASSWORD` |

**Docker Compose 架構（在 Playwright Repo）：**
- `app` - Spring Boot 應用（使用 GHCR image，profile 設為 `e2e`）
- `oracle-db` - Oracle 資料庫（container-registry.oracle.com/database/free:latest）
- `alloy` - Grafana Alloy（監控指標收集）

**CI/CD 觸發方式：**
- `repository_dispatch` 事件（當 Backend image 更新時觸發）
- `workflow_dispatch`（手動觸發）

---

## 🔧 測試執行指令

### build.gradle 配置

```groovy
tasks.named('test') {
    useJUnitPlatform {
        def excludeTag = System.getProperty('junit.platform.exclude.tags')
        if (excludeTag) {
            excludeTags excludeTag.split(',')
        }
    }
}
```

### 指令對照表

| 指令 | 執行範圍 | 說明 |
|------|----------|------|
| `./gradlew test` | **全部**（Unit + Integration） | 不帶參數 = 跑所有測試 |
| `./gradlew test -Djunit.platform.exclude.tags=IntegrationTest` | **只有 Unit Test** | 排除所有整合測試（最快） |
| `./gradlew test -Djunit.platform.exclude.tags=UnitTest` | **只有 Integration Test** | 排除所有單元測試 |
| `./gradlew test -Djunit.platform.exclude.tags=SanityTest` | Unit + Integration（排除 Sanity） | 排除 Context 載入測試 |

### 常用場景

```bash
# 開發中快速驗證業務邏輯（最快，不需 Docker）
./gradlew test -Djunit.platform.exclude.tags=IntegrationTest

# 完整測試（需要 Docker 環境給 Testcontainers）
./gradlew test

# 只跑整合測試（驗證資料庫相關邏輯）
./gradlew test -Djunit.platform.exclude.tags=UnitTest

# E2E 測試（在 Playwright Repository 中執行）
docker compose -f docker-compose.test.yml up -d
npm run test:e2e
docker compose -f docker-compose.test.yml down -v
```

---

## 📊 Profile 對照表

| Profile | 用途 | 資料庫 | Flyway | 使用場景 |
|---------|------|--------|--------|----------|
| `dev` | 本地開發 | Oracle (docker-compose.yml) | ✅ enabled | 開發者本機 |
| `integration-test` | 整合測試 | Oracle (Testcontainers) | ✅ enabled, validate | 本專案 `./gradlew test` |
| `unit-test` | 單元測試備用 | H2 (in-memory, MODE=Oracle) | ✅ enabled | 備用（目前 Unit Test 不啟動 Context） |
| `e2e` | E2E 測試 | Oracle (docker-compose.test.yml) | ✅ enabled | Playwright Repo |
| `openapi` | API 文件生成 | H2 | - | Gradle OpenAPI 插件 |

---

## 🏷️ Tag 設計

| Tag | 用途 | 對應測試類別 |
|-----|------|-------------|
| `UnitTest` | 純 Mockito 單元測試 | OrderServiceTest, AccountServiceTest, ProductServiceTest, OrderTransactionalServiceTest |
| `IntegrationTest` | 需要 Testcontainers 的整合測試 | OptimisticLockingIntegrationTest, DemoApplicationTests |
| `SanityTest` | Context 載入驗證（IntegrationTest 的子集） | DemoApplicationTests |

**注意：** `GlobalExceptionHandlerTest` 沒有 Tag（它不需要 Spring Context 也不需要 Mockito Extension，是直接 new 物件測試）。

---

## 🔑 關鍵設計決策

1. **Unit Test 不啟動 Spring Context** - 使用 `@ExtendWith(MockitoExtension.class)` 而非 `@SpringBootTest`，確保速度
2. **Integration Test 使用 Testcontainers** - 自動管理 Oracle 容器生命週期，不依賴外部環境
3. **E2E Test 分離到 Playwright Repo** - 前後端測試解耦，透過 Docker Compose 建立完整環境
4. **Tag 機制** - 可選擇性排除耗時的整合測試
5. **Flyway Migration** - 整合測試和 E2E 測試都使用 `db/migration/V1__initial_schema.sql` 確保 schema 一致性

---

## 🔗 相關文件

- [測試 Profile 配置指南](./test-profile-configuration-guide.md)
- [Docker Compose Test 說明](./docker-compose-test-explanation.md)
- [Playwright Repository 遷移指南](./playwright-repo-migration-guide.md)
- [測試資料初始化指南](./test-data-initialization-guide.md)

---

**最後更新**: 2026-05-26
**版本**: 1.0.0
