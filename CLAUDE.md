# CLAUDE.md

本檔案為 Claude Code (claude.ai/code) 在此儲存庫工作時提供指引。

## 權威文件

詳細指引已存放於 **`AGENTS.md`**，它以 `@`-import 匯入 **`docs/agents/`** 底下的模組化文件（overview、setup、git workflow、branch cleanup、code standards、architecture、dependency-check、testing、monitoring、troubleshooting、ai-tools-overview）。需要深入細節請閱讀那些文件；本檔案是快速上手摘要。當專案慣例變更時，請保持 `AGENTS.md`、`docs/agents/*` 與 `.github/instructions/Global.instructions.md` 同步（code-standards 文件有此要求）。

**規則**（語言、容器工具、shell 語法、git 慣例等）已提取至 `.claude/rules/project-rules.md`，自動套用，此處不重複。

## 常用指令

Build / run（從 repo 根目錄執行）：

```bash
./gradlew build                       # compile + test + assemble
./gradlew bootRun                     # run app (needs Oracle DB reachable)
./gradlew generateOpenApiDocs         # -> build/docs/swagger.json（見 openapi-doc-gen skill）
podman compose up -d                  # app + Oracle + Alloy + Prometheus + Grafana
podman compose up oracle-db -d        # DB only, then run DemoApplication.java from IDE
```

App 監聽於 **http://localhost:8787**。需要一份包含 `ORACLE_DEV_USERNAME` / `ORACLE_DEV_PASSWORD` 的 `.env`（見 `.env.example`）。`ORACLE_DB_HOST` 為選填 — 本機 IDE/`bootRun` 時可省略；`podman compose up` 會自動注入 `oracle-db`。見 `docs/agents/02-setup.md`。

Tests：

```bash
./gradlew test                                              # full suite (integration tests start Oracle via Testcontainers)
./gradlew test -Djunit.platform.exclude.tags=SanityTest     # CI gate 等效指令
./gradlew test --tests "*IntegrationTest"                   # only integration tests
./gradlew test --tests "com.ibm.demo.order.OrderServiceTest"  # single test class
```

> ⚠️ **Windows + podman 整合測試有已知坑**（記憶體競爭、provider 找不到、singleton 破壞）。執行前請參閱 **`integration-test-runner` skill**。

## 架構全貌

分層、以 domain 切片（feature-sliced）。三個 domain — **account**、**product**、**order** — 各自在 `com.ibm.demo.<domain>` 下擁有自己的 Controller / Service / Repository / Entity / DTO。橫切關注點放在專屬 package。

請求流程：`Client → Controller → Service → Repository → Entity`，共用基礎設施在 `util/`。

- **跨模組呼叫一律經由 `*Client` classes**（`AccountClient`、`ProductClient`、`OrderClient`），背後以 Spring `RestClient` 支撐，**不直接呼叫**其他 domain 的 service。
- **`OrderService` 與 `OrderTransactionalService`**：order 建立橫跨 account + product；transactional service 隔離 DB transaction 邊界與 orchestration 邏輯，編輯 order 流程時請保留此拆分。
- **Soft delete + auditing**：entity 以**組合（composition）**用 `@Embedded` 嵌入 `util/AuditMetadata`（audit 欄位）與 `util/SoftDeleteMetadata`（軟刪除欄位）；`@Version` optimistic locking 欄位因 JPA 不支援 `@Embeddable` 而**直接定義在各 entity**。需要 soft delete 的 repository 繼承 `util/SoftDeleteRepository`，`@SQLRestriction` 在 query 層自動過濾，**不要手刻 `deleted = false`**。（舊的 `BaseEntity` 繼承基底已移除 — 一律用組合，不要再引入 `@MappedSuperclass` 基底類別。）
- **分頁一致**：list endpoint 接受 `Pageable` 回傳 `PageResponse<T>`（預設 `page=0, size=20`），不提供非分頁列表。
- **錯誤處理**：拋出 `BusinessException` 並帶入對應的 `ErrorCode`（`new BusinessException(ErrorCode.X, "...")`）；`GlobalExceptionHandler` 統一對應 `ApiErrorResponse`。
- **Resilience4j**：`config/Resilience4jConfig.java`，service 方法上用 `@Bulkhead`、`@CircuitBreaker`、`@RateLimiter`。
- **可觀測性**：OTLP push → Grafana Alloy → Prometheus → Grafana。**無** `/actuator/prometheus` scrape endpoint。
- **Security**：stateless HTTP Basic，`anyRequest().authenticated()`；放行 actuator health、springdoc。`*Client` 自呼叫透過 loopback 繞回，`RestClientConfig` 掛 `internal` 帳號憑證。
- **DB migrations**：Flyway，`src/main/resources/db/migration`（Oracle）；H2 用於測試與 OpenAPI 產生。

> 新增 domain 請參閱 **`new-domain-scaffold` skill**。

## Profiles

設定採分層（env/system props > `application-{profile}.yml` > `application.yml`）。Profiles：`dev`、`unit-test`、`integration-test`、`e2e`、`openapi`。

## Git workflow

**Trunk-based。** 小步驟直接 commit 到 `main` — push 前 pre-push hook 自動執行 CI gate 相同的測試（`./gradlew test -Djunit.platform.exclude.tags=SanityTest`）。Push `main` 有副作用：發佈 image、觸發下游 E2E、重新產生 swagger.json。

commit message / PR body **不加 AI 協作者署名**（`Co-Authored-By: Claude`、`🤖 Generated with Claude Code` 等）；`.githooks/commit-msg` hook 會擋下含這些署名的 commit。

高風險變更（CI 改動、DB migrations、跨 domain 重構）請走 branch + PR — 詳見 **`high-risk-pr-workflow` skill**。
