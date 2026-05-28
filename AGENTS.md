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

#### 改動前的分支檢查流程

在進行任何程式碼改動前，必須遵循以下工作流程確保改動在正確的分支上進行：

##### 1. 檢查當前分支狀態

```bash
# 查看當前分支
git branch --show-current

# 查看工作區狀態
git status

# 列出所有本地分支
git branch -a
```

##### 2. 判斷改動類型並選擇分支策略

根據改動性質決定分支類型：

| 改動類型 | 分支前綴 | 說明 | 範例 |
|---------|---------|------|------|
| 新功能開發 | `feature/` | 新增業務功能或模組 | `feature/add-payment-module` |
| Bug 修復 | `bugfix/` | 修復非緊急的程式錯誤 | `bugfix/fix-order-calculation` |
| 緊急修復 | `hotfix/` | 修復生產環境的緊急問題 | `hotfix/security-patch` |
| 程式碼重構 | `refactor/` | 改善程式結構但不改變功能 | `refactor/optimize-query-performance` |
| 文件更新 | `docs/` | 僅更新文件內容 | `docs/update-api-guide` |
| 測試相關 | `test/` | 新增或修改測試案例 | `test/add-integration-tests` |
| 建置/工具 | `chore/` | 更新建置腳本、依賴版本等 | `chore/upgrade-spring-boot` |

##### 3. 分支選擇決策流程

**步驟 A: 檢查是否存在相關分支**

```bash
# PowerShell 環境
git branch -a | Select-String -Pattern "關鍵字"

# 範例：搜尋與訂單相關的分支（PowerShell）
git branch -a | Select-String -Pattern "order"

# Bash/Linux 環境
git branch -a | grep -i "關鍵字"
git branch -a | grep -i "order"
```

**步驟 B: 決策邏輯**

```
IF 存在相關且未合併的分支 THEN
    → 切換到該分支繼續開發
ELSE IF 當前在 main/master 分支 THEN
    → 必須建立新分支
ELSE IF 當前分支與改動類型不符 THEN
    → 建議建立新分支或切換到適當分支
ELSE
    → 可在當前分支繼續開發
END IF
```

##### 4. 分支操作指令

**情境 1: 切換到已存在的分支**

```bash
# 切換到本地分支
git checkout feature/add-payment-module

# 切換到遠端分支（首次）
git checkout -b feature/add-payment-module origin/feature/add-payment-module

# 更新分支到最新狀態
git pull origin feature/add-payment-module
```

**情境 2: 建立新分支**

**基底分支選擇策略**：
1. **優先選擇相關的功能分支作為基底**：如果新功能依賴於尚未合併的分支
2. **否則從 main 分支建立**：獨立功能或修復

```bash
# 策略 A: 從 main 建立新分支（獨立功能）
git checkout main
git pull origin main
git checkout -b feature/add-payment-module

# 策略 B: 從現有功能分支建立（依賴關係）
# 範例：新功能依賴於 feature/add-user-auth 的改動
git checkout feature/add-user-auth
git pull origin feature/add-user-auth
git checkout -b feature/add-payment-with-auth

# 策略 C: 從當前分支建立（延續當前工作）
git checkout -b feature/extend-current-work
```

**何時使用策略 B（從功能分支建立）**：
- 新功能需要使用尚未合併到 main 的程式碼
- 修復依賴於另一個未合併分支的 bug
- 在大型功能的子任務間建立依賴關係

**範例情境**：
```
main
 └─ feature/user-authentication (尚未合併)
     └─ feature/payment-with-auth (依賴 user-authentication)
```

**情境 3: 有未提交的改動需要切換分支**

```bash
# 方法 1: 暫存改動（推薦使用具名 stash）
git stash push -m "WIP: 描述當前工作"
git checkout target-branch
git stash list  # 查看 stash 列表
git stash pop   # 彈出最新的 stash
# 或指定特定的 stash
git stash apply stash@{0}

# 方法 2: 提交到臨時分支
git checkout -b temp/wip-backup
git add .
git commit -m "WIP: temporary backup"
git checkout target-branch
```

##### 5. 改動提交前的最終檢查

在提交前必須確認：

```bash
# 1. 確認當前分支正確
git branch --show-current

# 2. 檢查改動內容
git status
git diff

# 3. 確認沒有意外的檔案被追蹤
git ls-files --others --exclude-standard

# 4. 執行測試（依專案需求）
./gradlew test

# 5. 提交改動
git add <files>
git commit -m "type(scope): description"
```

##### 6. Agent 自動化建議流程

當 Agent 準備進行程式碼改動時，應自動執行以下檢查：

1. **偵測當前分支**: 使用 `execute_command` 執行 `git branch --show-current`
2. **分析改動性質**: 根據任務描述判斷改動類型（feature/bugfix/refactor 等）
3. **搜尋相關分支**: 執行 `git branch -a | grep -i "關鍵字"` 尋找相關分支
4. **提供建議**:
   - 若在 main 分支 → **必須**建議建立新分支
   - 若存在相關分支 → 建議切換到該分支
   - 若當前分支類型不符 → 建議建立新分支或切換分支
   - 若當前分支適合 → 確認後繼續
5. **等待使用者確認**: 使用 `ask_followup_question` 詢問使用者是否同意建議的分支操作
6. **執行分支操作**: 獲得確認後使用 `execute_command` 執行 Git 指令
7. **進行程式碼改動**: 分支確認無誤後才開始使用 `apply_diff` 或 `write_to_file`

**範例對話流程**:

```
Agent: 偵測到您當前在 main 分支，準備進行新功能開發。
建議建立新分支: feature/add-payment-module

是否要我執行以下指令？
git checkout -b feature/add-payment-module

User: 是

Agent: [執行 git checkout -b feature/add-payment-module]
分支建立成功，現在開始進行程式碼改動...
```

#### 分支命名規範

```
<類別>/<任務簡述>

類別前綴:
- feature/  : 新功能開發
- bugfix/   : Bug 修復
- hotfix/   : 緊急修復
- refactor/ : 程式碼重構
- docs/     : 文件更新
- test/     : 測試相關
- chore/    : 建置/工具更新

範例:
feature/add-payment-module
bugfix/fix-order-calculation
hotfix/security-patch
refactor/optimize-query-performance
docs/update-api-guide
test/add-integration-tests
chore/upgrade-spring-boot
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
test(order): add integration tests for order creation
chore(deps): upgrade Spring Boot to 3.5.14
```

#### 分支合併與清理

**推薦方式：使用 Pull Request**

```bash
# 1. 推送分支到遠端
git push origin feature/add-payment-module

# 2. 使用 Bob 的 /create-pr 指令建立 Pull Request（推薦）
# Bob 會自動：
#   - 切換到 Advanced 模式
#   - 生成 PR 描述
#   - 詢問目標分支（base branch）
#   - 建立 Pull Request

# 或手動使用 GitHub CLI
gh pr create --base main --head feature/add-payment-module --title "feat: add payment module" --body "詳細說明..."

# 3. 等待 Code Review 與 CI/CD 通過後，在 GitHub 上合併 PR

# 4. 合併後清理本地分支
git checkout main
git pull origin main
git branch -d feature/add-payment-module

# 5. 清理已刪除的遠端分支參考
git fetch --prune
```

**分支策略：短期分支原則**

**核心原則**：
- **所有功能分支都從 main 建立**，避免分支間依賴
- **盡快合併**，減少長期分支的維護成本
- **拆分大功能**為多個小 PR，每個獨立可測試

**實務做法**：

```
# ❌ 避免：建立依賴鏈
main
 └─ feature/user-auth
     └─ feature/payment (依賴 user-auth)

# ✅ 推薦：拆分獨立 PR
main
 ├─ feature/user-auth-api (先合併)
 ├─ feature/user-auth-ui (等 API 合併後建立)
 └─ feature/payment (等前面都合併後建立)
```

**如何處理功能依賴**：

1. **拆分功能**：將大功能分解為可獨立交付的小單元
2. **順序開發**：等前置功能合併後，再從最新的 main 建立新分支
3. **使用 Feature Flag**：未完成的功能用開關控制，允許提早合併

**範例：開發支付功能（依賴使用者認證）**

```bash
# 步驟 1: 開發並合併使用者認證 API
git checkout main
git pull origin main
git checkout -b feature/user-auth-api
# ... 開發 & 提交 ...
# 使用 /create-pr 合併到 main

# 步驟 2: 等 PR 合併後，開發使用者認證 UI
git checkout main
git pull origin main  # 取得最新的 user-auth-api
git checkout -b feature/user-auth-ui
# ... 開發 & 提交 ...
# 使用 /create-pr 合併到 main

# 步驟 3: 等 PR 合併後，開發支付功能
git checkout main
git pull origin main  # 取得完整的 user-auth
git checkout -b feature/payment
# ... 開發 & 提交 ...
# 使用 /create-pr 合併到 main
```

**特殊情況：必須並行開發時**

如果確實需要在功能分支上建立子分支（不推薦，但有時無法避免）：

```bash
# 從功能分支建立
git checkout feature/user-auth
git checkout -b feature/payment-on-auth

# 當 feature/user-auth 合併到 main 後
git checkout feature/payment-on-auth
git rebase main  # 將基底改為 main
git push origin feature/payment-on-auth --force-with-lease
# 使用 /create-pr 合併到 main
```

**Agent 偵測與建議**

當 Agent 偵測到使用者想從功能分支建立新分支時：

```
Agent: 偵測到您想從 feature/user-auth 建立新分支。

建議採用短期分支策略：
1. 將 feature/user-auth 拆分為更小的 PR 並先合併
2. 或等待 feature/user-auth 合併後，從 main 建立新分支

這樣可以：
- 避免複雜的合併順序
- 減少合併衝突
- 加快 Code Review 速度

是否要繼續從 feature/user-auth 建立？（不推薦）
```

**緊急情況：本地直接合併（不推薦）**

僅在緊急情況或個人專案使用：

```bash
git checkout main
git pull origin main
git merge --no-ff feature/add-payment-module
git push origin main
git branch -d feature/add-payment-module
```

**Agent 建議流程**

當改動完成準備合併時，Agent 應：
1. 確認所有改動已提交
2. **檢查是否從功能分支建立**：如果是，提醒考慮重新從 main 建立
3. 建議推送分支到遠端：`git push origin <branch-name>`
4. **優先建議使用者執行 `/create-pr` 指令**（Bob Agent 專用）或使用 GitHub CLI
5. 提醒使用者等待 Code Review 與 CI/CD 檢查
6. 合併後才建議清理本地分支

**Bob Agent 的 /create-pr 指令流程**：
- 自動切換到 Advanced 模式使用 MCP 工具
- 分析 git diff 生成 PR 描述
- 詢問目標分支（從現有分支列表選擇）
- 生成有意義的 PR 標題
- 詢問是否要編輯 PR 描述
- 建立 Pull Request 並返回 URL
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

### Agent 文件維護規範

#### 文件同步更新原則
1. **跨 Agent 文件同步**: 當對話過程中更新任何 Agent 相關文件時，必須確保所有類型的 Agent 文件都同步更新，包括：
   - `AGENTS.md` (Cline、Bob、Gemini CLI 等通用 Agent)
   - `.github/instructions/Global.instructions.md` (GitHub Copilot)
   - 其他專案特定的 Agent 配置檔案

2. **文件過時檢測**: 在對話開始前、進行中或結束後，若發現文件內容已經過時或不符合實際情況，應：
   - 立即標記過時的內容
   - 提出更新建議
   - 在獲得確認後同步更新所有相關 Agent 文件
   - 記錄更新日期與變更原因

3. **一致性驗證**: 定期檢查各 Agent 文件間的規範是否一致，特別是：
   - 語言偏好設定
   - 工具使用規範（如 podman vs docker）
   - 程式碼風格與架構原則
   - Git 工作流程規範

4. **更新觸發時機**:
   - 專案架構或技術棧變更時
   - 開發規範或最佳實踐更新時
   - 發現文件與實際情況不符時
   - 新增或移除開發工具時

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

**最後更新**: 2026-05-28
**維護者**: Bobby
