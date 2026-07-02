# CLAUDE.md

本檔案為 Claude Code (claude.ai/code) 在此儲存庫工作時提供指引。

## 權威文件

詳細指引已存放於 **`AGENTS.md`**，它以 `@`-import 匯入 **`docs/agents/`** 底下的模組化文件（overview、setup、git workflow、branch cleanup、code standards、architecture、dependency-check、testing、monitoring、troubleshooting、ai-tools-overview）。需要深入細節請閱讀那些文件；本檔案是快速上手摘要。當專案慣例變更時，請保持 `AGENTS.md`、`docs/agents/*` 與 `.github/instructions/Global.instructions.md` 同步（code-standards 文件有此要求）。

## 影響每項任務的慣例

- **以繁體中文回應**；技術術語保留英文。
- **容器一律使用 `podman`，不用 `docker`。** 所有 compose/build 指令都以 `podman compose` 為準。
- **執行 CLI 指令前先偵測 shell。** PowerShell 以 `;` 串接並呼叫 `./gradlew`；CMD 以 `&&` 串接並呼叫 `gradlew`。不要混用語法。（本 session 的 shell 是 Windows 上的 Git Bash — 請使用 `./gradlew`。）
- 單模組 Gradle 專案（`settings.gradle` 定義單一 project）。Java 21 toolchain。base package 為 `com.ibm.demo`。

## 常用指令

Build / run（從 repo 根目錄執行）：

```bash
./gradlew build                       # compile + test + assemble
./gradlew bootRun                     # run app (needs Oracle DB reachable)
./gradlew generateOpenApiDocs         # -> build/docs/swagger.json (uses 'openapi' profile)
podman compose up -d                  # app + Oracle + Alloy + Prometheus + Grafana
podman compose up oracle-db -d        # DB only, then run DemoApplication.java from IDE
```

App 監聽於 **http://localhost:8787**。需要一份包含 `ORACLE_DEV_USERNAME` / `ORACLE_DEV_PASSWORD` 的 `.env`（見 `.env.example`）。`ORACLE_DB_HOST` 為選填 — 本機 IDE/`bootRun` 時可省略（dev datasource URL 會 fallback 到 `localhost`）；`podman compose up` 會自動注入 `oracle-db`。見 `docs/agents/02-setup.md`。

Tests：

```bash
./gradlew test                                              # full suite (integration tests start Oracle via Testcontainers)
./gradlew test -Djunit.platform.exclude.tags=SanityTest     # exclude a JUnit tag (see build.gradle wiring)
./gradlew test --tests "*IntegrationTest"                   # only integration tests
./gradlew test --tests "com.ibm.demo.order.OrderServiceTest"  # single test class
```

注意事項：
- Integration tests 繼承 `BaseIntegrationTest`，它透過 **singleton `static` block** + `@ServiceConnection`（profile `integration-test`）啟動單一 `gvenzl/oracle-free` container；首次執行會下載 image，啟動可能需要數分鐘。**請勿改成 `@Testcontainers`/`@Container`** — 該 lifecycle 會在第一個 test class 後停止 container，而 Spring 會重用快取的 context，導致後續 integration test class 失敗。見 `docs/agents/08-testing.md`。
- `test` task 強制 `maxParallelForks = 1` 以避免 Oracle container 競用 — 不要預期會有平行測試執行。
- Tag 過濾由 `junit.platform.exclude.tags` system property 驅動（在 `build.gradle` 中接線）。
- **Windows + podman**：執行 integration tests 前，先跑 `podman compose stop` — 兩個並行的 Oracle container（compose + Testcontainers）會耗盡預設 2GiB 的 podman machine，第二個會靜默地拒絕連線。另外將 `~/.testcontainers.properties` 的 `docker.host` 指向 podman pipe。純文件的 push 可略過此步（test task 為 UP-TO-DATE，不啟動 container）。見 `docs/agents/08-testing.md`。

## 架構全貌

分層、以 domain 切片（feature-sliced）。三個 domain — **account**、**product**、**order** — 各自在 `com.ibm.demo.<domain>` 下擁有自己的 Controller / Service / Repository / Entity / DTO。橫切關注點（cross-cutting concerns）放在專屬 package。

請求流程：`Client → Controller → Service → Repository → Entity`，共用基礎設施在 `util/`。

- **跨模組呼叫一律經由 `*Client` classes**（例如 `AccountClient`、`ProductClient`、`OrderClient`），背後以 Spring `RestClient` 支撐（設定於 `config/RestClientConfig.java`），**而非**直接呼叫另一個 domain 的 service。這正是讓 app 能把各模組當成獨立服務看待的機制。
- **`OrderService` 與 `OrderTransactionalService`**：order 建立橫跨 account + product；transactional service 把 DB transaction 邊界與 orchestration/remote-call 邏輯隔離開來。編輯 order 流程時請保留此拆分。
- **Soft delete + auditing** 已集中化：entity 繼承 `util/BaseEntity`（audit 欄位、`@Version` optimistic locking），需要 soft delete 的 repository 繼承 `util/SoftDeleteRepository`，Hibernate `@SQLRestriction` 在 query 層過濾掉已刪除/停用的 row。不要自行手刻 `deleted = false` 過濾。（`BaseEntity` 已標記 `@Deprecated(forRemoval=true)` — 為新 entity 繼承前請先確認。）
- **分頁一致**：list endpoint 接受 `Pageable` 並回傳 `util/PageResponse<T>`（預設 `page=0, size=20`）。刻意不提供非分頁的 list endpoint。
- **錯誤處理**：domain 邏輯拋出 `exception/BusinessLogicCheck/BusinessException` 的子類別；`GlobalExceptionHandler`（`@RestControllerAdvice`）使用 `util/ErrorCode` 將它們對應為 `ApiErrorResponse`。新增失敗情境時請新增 `BusinessException` 子類別，而非臨時湊出的 response。
- **Resilience4j**（`config/Resilience4jConfig.java`、`application.yml`）透過 service 上的 annotation 提供 Bulkhead（fail-fast，`max-wait-duration: 0`）、CircuitBreaker 與 RateLimiter。設定 key 放在 `resilience4j.*` 底下。
- **可觀測性（Observability）**：app 透過 OTLP（Micrometer）匯出 metrics → Grafana Alloy → Prometheus → Grafana。相關基礎設施檔案：`docker-compose.yml`、`config.alloy`、`prometheus.yml`。注意：監控是 **OTLP push**（outbound）— 沒有 `/actuator/prometheus` scrape endpoint（未引入 prometheus registry 相依）。
- **安全性（Security）**：`config/SecurityConfig.java` 提供一條 stateless HTTP Basic `SecurityFilterChain` — `anyRequest().authenticated()`，並放行 `actuator health`（`/actuator/health/**`，供 Dockerfile HEALTHCHECK 探針）+ springdoc（`/v3/api-docs/**`、`/swagger-ui/**`）。使用者為 in-memory（`api`、`internal`），憑證來自 `app.auth.*`（可用 env 覆寫）。此 security filter chain 只攔截 **inbound HTTP** — Oracle/Hikari、OTLP metric push 與 container 網路皆不受影響。由於 `*Client` 呼叫會透過 HTTP 迴繞回本 app，`RestClientConfig` 會附上 `internal` 帳號的 Basic 憑證，讓自我呼叫能通過 filter chain。另有一條 `@Profile("openapi")` 的 permit-all chain，使 `generateOpenApiDocs` 得以運作。
- **DB migrations**：Flyway，scripts 位於 `src/main/resources/db/migration`（Oracle）。H2 用於測試與 OpenAPI 文件產生。

## Profiles

設定採分層（env/system props > `application-{profile}.yml` > `application.yml`）。Profiles：`dev`、`unit-test`、`integration-test`、`e2e`、`openapi`。測試資源存放 `-unit-test`、`-integration-test` 與 `-e2e` 變體。

## Git workflow（摘要）

**Trunk-based、單人專案。** 預設把小步驟**直接 commit 到 `main`** 並 push — CI（`.github/workflows/image-publish.yml`）在 push-to-`main` 與 PR 兩種情況都會跑 test gate，所以不需要為了觸發測試而開 PR。`main` 沒有 branch protection；若壞掉就 fix-forward 或 `git revert`。

- **Push 前，測試必須通過。** 一個 pre-push hook（`.githooks/pre-push`，以 `git config core.hooksPath .githooks` 啟用一次）會在 push 含 `main` 時執行與 CI gate 相同的指令（`./gradlew test -Djunit.platform.exclude.tags=SanityTest`）— 「本機綠燈 = CI 綠燈」。
- **Push `main` 有副作用**：它會發佈 `latest` image 到 ghcr.io、觸發下游 E2E，並重新產生 swagger.json。test gate 在 image build 之前執行，所以被測試攔下的失敗不會出貨壞掉的 image。
- **只在高風險變更時才開 branch + PR**：CI workflow 編輯、DB migrations、跨 domain 重構 / 大型功能 — 任何你想讓 CI 在進入 `main` 前先驗證的變更。
- **開 branch 時**，使用 `feature/ fix/ hotfix/ refactor/ config/ docs/ test/ chore/` 前綴（小寫、以 `-` 分隔），從最新的 `main` 分出，保持短命。Commits 遵循 Conventional Commits（`type(scope): subject`，祈使句、小寫、結尾不加句點）。

完整規則、pre-push hook 設定、PR labels 與清理步驟見 `docs/agents/03-git-workflow.md` 與 `04-git-branch-cleanup.md`。透過 GitHub MCP 新增 PR label 時，使用 `issue_write`（method `update`）並帶入 PR 編號 — `create_pull_request`/`update_pull_request` 沒有 labels 欄位。
