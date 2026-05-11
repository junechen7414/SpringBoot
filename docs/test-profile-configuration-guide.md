# 測試 Profile 配置指南

## 問題說明

你遇到了兩個不同測試場景的配置衝突：

### 場景 1: 單元測試 (GitHub Actions)
- **位置**: SpringBoot repository
- **執行命令**: `./gradlew test`
- **應使用資料庫**: H2 (內存資料庫)
- **Profile**: `test`
- **錯誤**: H2 不支援 Oracle 特定的 SQL 語法

### 場景 2: E2E 測試 (Playwright + Docker Compose)
- **位置**: Playwright repository
- **執行方式**: Docker Compose + Playwright
- **應使用資料庫**: Oracle (透過 docker-compose)
- **Profile**: 原本使用 `test`，但應改為 `e2e`
- **錯誤**: `ORA-02289: sequence does not exist`

## 根本原因

**兩種測試場景使用了相同的 profile (`test`)，但需要不同的資料庫配置！**

- 單元測試需要快速、獨立的 H2 資料庫
- E2E 測試需要真實的 Oracle 資料庫環境

## 解決方案

### ✅ 已完成的修改

#### 1. SpringBoot Repository - 單元測試配置

**檔案**: `src/test/resources/application-test.yml`
```yaml
# Unit Test Profile - Uses H2 in-memory database
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password: 
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.H2Dialect
    show-sql: true
  flyway:
    enabled: false
```

**用途**: 
- GitHub Actions 執行 `./gradlew test` 時使用
- 所有 `@ActiveProfiles("test")` 的測試類別
- 快速、不需要外部依賴

#### 2. SpringBoot Repository - E2E 測試配置

**檔案**: `src/test/resources/application-e2e.yml` (新建)
```yaml
# E2E Test Profile - Uses Oracle database in Docker
spring:
  datasource:
    url: jdbc:oracle:thin:@//oracle-db:1521/FREEPDB1
    username: ${ORACLE_TEST_USERNAME}
    password: ${ORACLE_TEST_PASSWORD}
    driver-class-name: oracle.jdbc.driver.OracleDriver
    hikari:
      connection-timeout: 5000
      maximum-pool-size: 20
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.OracleDialect
    show-sql: true
  flyway:
    enabled: false
```

**用途**:
- Playwright E2E 測試時使用
- 需要在 Playwright repo 的 docker-compose 中設定 `SPRING_PROFILES_ACTIVE=e2e`

### 🔧 需要在 Playwright Repository 進行的修改

#### 修改 docker-compose.test.yml

在 Playwright repository 的 `docker-compose.test.yml` 中，修改 Spring Boot app 服務：

```yaml
services:
  app:
    image: ghcr.io/${GITHUB_REPOSITORY_OWNER:-junechen7414}/springboot:latest
    pull_policy: always
    container_name: spring-boot-app-test
    ports:
      - "8787:8787"
    environment:
      # 🔴 關鍵修改：從 test 改為 e2e
      - SPRING_PROFILES_ACTIVE=e2e
      - SPRING_DATASOURCE_URL=jdbc:oracle:thin:@oracle-db:1521/FREEPDB1
      - SPRING_DATASOURCE_USERNAME=${ORACLE_TEST_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${ORACLE_TEST_PASSWORD}
    networks:
      - test-net
    depends_on:
      oracle-db:
        condition: service_healthy

  oracle-db:
    image: container-registry.oracle.com/database/free:latest
    container_name: oracle-db-test
    ports:
      - "1521:1521"
    environment:
      - ORACLE_SID=FREE
      - ORACLE_PDB=FREEPDB1
      - ORACLE_TEST_USERNAME=${ORACLE_TEST_USERNAME}
      - ORACLE_TEST_PASSWORD=${ORACLE_TEST_PASSWORD}
    volumes:
      - ./init-scripts:/opt/oracle/scripts/startup
      - ./init-scripts-template:/opt/oracle/template
    healthcheck:
      test: [ "CMD-SHELL", "sqlplus -L ${ORACLE_TEST_USERNAME}/${ORACLE_TEST_PASSWORD}@FREEPDB1 <<<'exit;'" ]
      interval: 30s
      timeout: 10s
      retries: 20
      start_period: 90s
    networks:
      - test-net

networks:
  test-net:
    driver: bridge
```

## Profile 使用總覽

| Profile | 用途 | 資料庫 | 使用場景 |
|---------|------|--------|----------|
| `dev` | 本地開發 | Oracle (docker-compose.yml) | 開發者本機 |
| `test` | 單元測試 | H2 (內存) | GitHub Actions, 本地測試 |
| `e2e` | E2E 測試 | Oracle (docker-compose.test.yml) | Playwright E2E 測試 |
| `openapi` | API 文件生成 | H2 (內存) | Gradle OpenAPI 插件 |

## 測試執行方式

### 單元測試 (SpringBoot Repository)
```bash
# 使用 test profile (H2 資料庫)
./gradlew test

# 排除特定標籤的測試
./gradlew test -Djunit.platform.exclude.tags=SanityTest
```

### E2E 測試 (Playwright Repository)
```bash
# 啟動服務 (使用 e2e profile + Oracle)
docker compose -f docker-compose.test.yml up -d

# 執行 Playwright 測試
npm run test:e2e

# 清理
docker compose -f docker-compose.test.yml down -v
```

## 常見問題

### Q1: 為什麼單元測試會出現 H2 語法錯誤？
**A**: 因為之前 `application-test.yml` 配置了 Oracle，但單元測試環境沒有 Oracle。現在已改為 H2。

### Q2: 為什麼 E2E 測試出現 "sequence does not exist"？
**A**: 因為 E2E 測試使用了 `test` profile，但該 profile 現在配置為 H2。需要改用 `e2e` profile。

### Q3: 如何確認使用了正確的 profile？
**A**: 查看應用啟動日誌：
```
The following 1 profile is active: "e2e"
```

### Q4: 本地開發時應該用哪個 profile？
**A**: 使用 `dev` profile (預設)，透過 `docker-compose.yml` 啟動 Oracle。

## E2E Profile 驗證說明

**重要**: `application-e2e.yml` 配置文件**不在**此 SpringBoot repository 中進行測試，原因如下：

1. **環境限制**: 單元測試環境無法連接到 Oracle 數據庫
2. **依賴外部設施**: e2e profile 需要 docker-compose 環境（來自 Playwright repository）
3. **測試範圍**: 此 profile 的完整驗證需要 E2E 測試基礎設施

### E2E Profile 測試位置

e2e profile 的驗證在 **Playwright repository** 中進行：
- 通過 `docker-compose.test.yml` 啟動完整的測試環境（包括 Oracle 數據庫）
- Playwright E2E 測試會實際使用此配置連接 Oracle
- 任何配置錯誤會在 Playwright 測試執行時被發現

### 修改 E2E Profile 後的驗證步驟

如果修改了 `application-e2e.yml`，請在 Playwright repository 中驗證：

```bash
# 在 Playwright repository 中
docker compose -f docker-compose.test.yml up -d
npm run test:e2e
docker compose -f docker-compose.test.yml down -v
```

## 檢查清單

### SpringBoot Repository ✅
- [x] `application-test.yml` 配置為 H2
- [x] `application-e2e.yml` 配置為 Oracle
- [x] 單元測試使用 `@ActiveProfiles("test")`
- [x] `application-e2e.yml` 包含清晰的文檔說明其用途和測試位置

### Playwright Repository (待修改)
- [ ] `docker-compose.test.yml` 設定 `SPRING_PROFILES_ACTIVE=e2e`
- [ ] GitHub Actions workflow 設定正確的環境變數
- [ ] 確認 GitHub Secrets 包含 `ORACLE_TEST_USERNAME` 和 `ORACLE_TEST_PASSWORD`
- [ ] E2E 測試驗證 e2e profile 配置正確

## 相關文件

- [Docker Compose Test 說明](./docker-compose-test-explanation.md)
- [Playwright Repository 遷移指南](./playwright-repo-migration-guide.md)
- [測試資料初始化指南](./test-data-initialization-guide.md)