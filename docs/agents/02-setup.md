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
