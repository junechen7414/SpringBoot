## 建置與執行

### 前置需求

- **Java 25** (建議使用 Eclipse Temurin) — 唯一權威來源是 `build.gradle` 的 toolchain 宣告，此處只是副本
- **Podman** 或 Docker (用於容器管理)
- **Gradle 9.6.1** (專案已包含 Gradle Wrapper，無需自行安裝)

### JDK 版本管理

以下是與版本無關的通則，未來要升級到新的 Java 版本、或想清掉機器上舊的 JDK 時照著走。

#### 一台機器上的 JDK 有兩種角色

版本相關的困惑幾乎都來自沒分清這兩件事：

| 角色 | 由誰決定 | 版本要求 |
|---|---|---|
| **launcher** — 啟動 Gradle / IDE 的那個 JVM | `JAVA_HOME`（未設則 PATH 上第一個 `java`） | 只要該 Gradle 版本支援即可，**不必**等於編譯版本 |
| **toolchain** — 實際用來編譯的 JDK | `build.gradle` 的 `JavaLanguageVersion.of(N)` | **主版本精確比對** |

兩者允許不同（daemon 跑舊版、編譯用新版），但不一致時會出現難查的症狀。實例：springdoc 的
`forkedSpringBootRun` 預設用 daemon 的 JVM 啟動 app，若它比 toolchain 舊，app 會在啟動時
`UnsupportedClassVersionError`，而 `generateOpenApiDocs` 只會顯示連不上 `/v3/api-docs` 的 timeout
——`build.gradle` 已明確綁定該 task 的 launcher 來消除這個落差，升版時不需重新處理。

#### 建議的機器設定

- `JAVA_HOME` 指向目前主力開發的 JDK；PATH 只放 `%JAVA_HOME%\bin`（Windows）或 `$JAVA_HOME/bin`。
  **不要逐版本硬塞 bin 目錄** —— 換版時只需改 `JAVA_HOME` 一處，不必記得同步 PATH。
- 多版本共存交給使用者層級的 `~/.gradle/gradle.properties`：

  ```properties
  org.gradle.java.installations.paths=<JDK-A-home>,<JDK-B-home>
  ```

  填 **JDK home，不是 `bin`**；Windows 上 `.properties` 的反斜線要寫 `\\`（單一 `\b` 會被當成轉義序列吃掉）。
  用 `./gradlew -q javaToolchains` 確認偵測結果 —— Gradle 只看得到這裡列出的版本加上當前 JVM，
  機器上裝了但沒列出的 JDK 一律不會被挑到。
- IDE 若要直接執行 `DemoApplication.java`（見下方「僅啟動資料庫」），需另外註冊 runtime：
  VS Code 的 `java.configuration.runtimes`（主力版本加 `"default": true`）、IntelliJ 的 Project SDK。
  **這份設定不在版控內**，換機器或升版時最容易漏掉。
- 改完 `JAVA_HOME` 後務必 `./gradlew --stop`，否則既有 daemon 會繼續用舊 JVM 服務，
  看起來像設定沒生效。

#### 升級到新的 Java 版本

前兩項是擋門檻，沒過就不要往下做：

1. **確認 Gradle 支援該 Java 版本**（見 Gradle 相容性矩陣）。不支援就先升 Gradle。
2. **確認 annotation processor 支援** —— 實務上 **Lombok 最常擋路**，它依賴 JDK 內部 API，
   每個新版本都得等它出新 release。
3. **同時**改三處宣告；漏一處會變成「本機過、CI 過、映像跑不起來」這種難查的組合：
   - `build.gradle` 的 toolchain
   - `Dockerfile` 兩個階段的 base image tag
   - `.github/workflows/image-publish.yml` 兩個 job 的 `setup-java`
4. 本機：裝新 JDK → 加進 `installations.paths` → 調 `JAVA_HOME` → IDE 註冊 runtime → `./gradlew --stop`。
5. 驗三關：

   ```bash
   ./gradlew clean build -x test                                # 一
   ./gradlew test -Djunit.platform.exclude.tags=SanityTest      # 二（CI gate 等效指令）
   ./gradlew generateOpenApiDocs                                # 三
   ```

   第三關最容易被 toolchain 變更影響（見上面 `forkedSpringBootRun` 的例子），務必跑。
   第一關順便確認產出的 class file major version = **44 + Java 版本**（17→61、21→65、25→69）：

   ```powershell
   # 讀 class 檔第 7-8 個 byte；Java 25 應得到 0 69
   [System.IO.File]::ReadAllBytes('build\classes\java\main\com\ibm\demo\DemoApplication.class')[6..7]
   ```

6. 同步版本敘述：本檔前置需求、`01-overview.md`、`.claude/rules/project-rules.md`。

#### 舊版 JDK 何時可以解除安裝

條件是「**這台機器上所有專案宣告的 toolchain 版本都不再包含它**」。要注意：

- toolchain 是精確比對，**裝了新版不代表可以刪舊版**。只要還有專案宣告 `of(21)`，機器上僅有更新版
  就會直接失敗：`No matching toolchains found for requested specification: {languageVersion=21}`。
- 本專案**未安裝 foojay toolchain resolver**，所以缺版本是硬失敗，Gradle 不會自動下載補救。
- 刪之前掃過這些地方（**後兩項不在版控裡，掃 repo 掃不到**）：各專案建置檔的版本宣告
  （`JavaLanguageVersion.of(`、`sourceCompatibility`、`<java.version>`、`<maven.compiler.*>`）、
  `.sdkmanrc` / `.java-version`、CI workflow、`Dockerfile`、IDE runtime 設定、
  `~/.gradle/gradle.properties`。
- 一個 JDK 約 300MB，回收的空間有限；主要好處是少一個「現在到底在用哪個 java」的混淆來源。

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

然後在 IDE 中執行 `DemoApplication.java`，應用會透過 `localhost:1521`（compose 已將容器 1521 埠映射到宿主機）連接到 Oracle DB。

> **資料庫主機名如何切換（`ORACLE_DB_HOST`）**：`application-dev.yml` 的 datasource URL 寫成
> `jdbc:oracle:thin:@//${ORACLE_DB_HOST:localhost}:1521/FREEPDB1`，只把「主機名」抽成可覆寫的變數：
> - **本地 IDE / `bootRun`**：不設定該變數，自動 fallback 成 `localhost`（需先 `podman compose up oracle-db -d` 把 1521 埠映射出來）。
> - **容器內（`podman compose up`）**：`docker-compose.yml` 自動帶入 `ORACLE_DB_HOST=oracle-db`（服務名），容器間透過內建 DNS 解析連線。
>
> 之所以這樣設計，是因為本地直連時用容器服務名 `oracle-db` 無法解析；URL 格式集中在 yml 一份，只有主機名隨環境不同。

#### 3. 環境變數配置

建立 `.env` 檔案於專案根目錄：

```env
ORACLE_DEV_USERNAME=your_username
ORACLE_DEV_PASSWORD=your_password

# HTTP Basic 認證（Spring Security）。本地 bootRun 未設定時，application.yml 有 dev 友善預設值；
# 正式/共享環境務必覆蓋。api-*：一般 API 呼叫端；internal-*：內部 *Client loopback 自呼叫服務帳號。
# 這兩組是機器帳號，密碼以 {noop} 逐字比對、不做雜湊（理由見 SecurityConfig 註解）。
API_USERNAME=api
API_PASSWORD=change_me_api
INTERNAL_USERNAME=internal
INTERNAL_PASSWORD=change_me_internal
```

> **呼叫受保護的 API**：加了 Spring Security 後，除了 `actuator health`（`/actuator/health/**`）與 springdoc 文件端點外，
> 其餘端點都需要 HTTP Basic 認證，例如 `curl -u api:<密碼> http://localhost:8787/account/1`。
> 內部 `*Client` 的 loopback 自呼叫由 `RestClientConfig` 自動帶入 `internal` 帳號憑證，無須手動處理。

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
- **第一階段**: 以 `eclipse-temurin:25-jdk-alpine` 為基礎映像（精簡 JDK 25），透過 Gradle Wrapper (9.6.1) 編譯並打包 JAR (跳過測試)
- **第二階段**: 使用 `eclipse-temurin:25-jre-alpine` 執行，最終映像檔僅包含 JRE 與應用程式

#### 映像內建 HEALTHCHECK（重要契約）

最終映像在 `Dockerfile` 內定義了 **container-level `HEALTHCHECK`**，以 `wget --spider` 探測 `/actuator/health`：

```dockerfile
HEALTHCHECK --interval=30s --timeout=30s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8787/actuator/health || exit 1
```

- **誰依賴它**: 下游自動化測試 / E2E repo 直接消費此映像，並依賴**映像自帶的健康狀態**（`docker ps` 顯示的 `(healthy)`）判斷應用是否就緒。修改或移除此 `HEALTHCHECK` 屬於對下游的**破壞性變更**。
- **`/actuator/health` 含 `db` 元件**: 容器要等資料庫連線就緒才會回報 `healthy`，這正是下游要的「真正 ready」語意。
- **`start-period=60s`**: 預留冷啟動緩衝（Spring Boot + DB 連線建立），避免啟動期間的探測失敗被計入 `retries` 而誤判 unhealthy。
- ⚠️ **Image format 注意事項**: 此 `HEALTHCHECK` 由 **Docker Buildx（CI 發佈管線 `image-publish.yml` 使用）與 Docker runtime 完整保留**；但 **`podman build` 預設的 OCI 格式會將其剝除**（建置時會出現 `HEALTHCHECK is not supported for OCI image format` 警告）。若改用 podman/buildah 發佈映像，必須加上 `--format docker`，否則下游依賴的健康狀態會無聲消失。

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
