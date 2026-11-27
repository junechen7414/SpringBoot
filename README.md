# SpringBoot 訂單管理練習

從 Java／Spring Boot 的訂單管理作業出發，逐步加入交易處理、跨模組 HTTP 呼叫、容錯、自動化測試與監控的後端練習專案。

目前以 **一個 Spring Boot 應用程式＋Oracle** 執行，程式依 Account、Product、Order 分組；模組之間保留 HTTP 協作，讓認證、連線池、逾時與錯誤轉譯都能在本機觀察。搭配獨立的 [Playwright 測試 repo](https://github.com/junechen7414/Playwright-TS)，練習從後端建置、映像發布到下游自動驗證的整段流程。

- 想先執行：看[本機啟動](#本機啟動)。
- 想理解設計：看[作業起點與架構](#作業起點與架構)。
- 想跑測試或理解發布：看[測試與 API 文件](#測試與-api-文件)、[CI 與下游整合](#ci-與下游整合)。

## 作業起點與架構

原練習以訂單、商品與 Account 三個平台為主，練習 CRUD、請求驗證、Oracle／JPA、Controller／Service／Repository 分層，以及模組間的 HTTP 呼叫；進階部分再加入業務檢核、交易、全域例外處理與 Service 單元測試。

這份實作沿著上述目標演進。目前採 WebMVC 與 RestClient，並加入 Flyway、樂觀鎖、Resilience4j、契約測試、Testcontainers、容器與監控。這些後續選擇不全是原作業的指定做法，也不代表三個平台目前各自獨立部署。

### 呼叫與交易怎麼分工

- **Controller → Service → Repository → Entity**：Controller 處理 HTTP 與輸入驗證，業務規則放 Service，資料操作交給 Repository。
- **跨模組走 `*Client`**：[RestClientConfig](src/main/java/com/ibm/demo/config/RestClientConfig.java) 為宣告式 HTTP interface 建立 proxy；base URL 指回本應用，因此一次外呼會成為新的 inbound 請求，再經過 Security filter chain。
- **Order 編排與 DB 交易分開**：[OrderService](src/main/java/com/ibm/demo/order/OrderService.java) 負責跨模組呼叫與補償；[OrderTransactionalService](src/main/java/com/ibm/demo/order/OrderTransactionalService.java) 負責 DB-only 交易，避免把 HTTP 往返包進持有 DB 連線的交易。
- **Account 停用由中立流程協調**：[AccountLifecycleService](src/main/java/com/ibm/demo/orchestration/AccountLifecycleService.java) 組合買方停用與訂單檢查，避免 Account、Order 直接互相依賴。
- **資料與 HTTP 契約各自驗證**：`@Version` 處理版本衝突，Clock 與 auditing 統一時間；對外錯誤用 RFC 9457 ProblemDetail，列表用 `PageResponse`。

HTTP 呼叫不會自動延伸原本的 DB 交易。庫存與訂單分段提交，因此程式有失敗補償；它是這份練習的重要觀察點，不能把補償當成跨服務原子交易或必然成功的保證。

資料生命週期的取捨見 [ADR 0001：軟刪除的範圍](docs/adr/0001-soft-delete-only-for-termination.md)、[ADR 0002：終止狀態](docs/adr/0002-termination-recorded-only-by-deleted.md)；RestClient、容錯與組合式 metadata 的理由見[設計與疑難排解](docs/agents/10-troubleshooting.md)。

## 目前功能與業務範圍

| 範圍 | 目前提供的行為 |
| --- | --- |
| Account | 建立、查詢、更新買方資料；還有 Active Order 時不能停用。Account 不是登入帳號。 |
| Product | 建立、查詢、更新商品與可用量；還有 Reservation 時不能下架。 |
| Order | 建立、查詢、更新與取消訂單；建立／更新時調整 Reservation，取消時歸還。 |
| 終止紀錄 | Deactivate、Delist、Cancel 採不可恢復的軟刪除；一般查詢不回傳已終止資料。 |
| API | 輸入驗證、分頁、具名回應、統一錯誤格式，以及執行時 OpenAPI／Swagger UI。 |

本專案沒有支付、出貨或結單流程，Reservation 不會轉成已售出。完整名詞與關係以 [CONTEXT.md](CONTEXT.md) 為準，實際端點以執行中的 OpenAPI 為準。

## 技術基礎

| 類別 | 目前採用 |
| --- | --- |
| Java／建置 | Java 25、Virtual Threads、Gradle Wrapper 9.6.1 |
| 後端 | Spring Boot 4.1.2、WebMVC、Validation、Spring Security |
| HTTP 協作 | RestClient、宣告式 HTTP interface、Apache HttpClient 5 連線池 |
| 資料 | Oracle Database Free、Spring Data JPA、Flyway；H2 用於輕量文件生成等用途 |
| 容錯 | Resilience4j：Bulkhead、Rate Limiter、Circuit Breaker |
| 測試 | JUnit、Mockito、MockMvc、Testcontainers Oracle |
| 監控 | Actuator、Micrometer／OpenTelemetry、Alloy、Prometheus、Tempo、Grafana |
| 發布 | GitHub Actions、GHCR，與 Playwright repo 串接 |

版本宣告以 [build.gradle](build.gradle) 與 [Gradle Wrapper 設定](gradle/wrapper/gradle-wrapper.properties) 為準；不需要另外安裝全域 Gradle。

## 本機啟動

前置條件：JDK 25、可運作的 Podman 與 Compose provider，以及足夠執行 Oracle 的記憶體。Windows／WSL 與 Testcontainers 的已知環境問題，先看[環境說明](docs/agents/02-setup.md)及[測試注意事項](docs/agents/08-testing.md)。

以下指令從 repo 根目錄執行，以 PowerShell 為例。兩種啟動方式擇一；應用使用 `8787`、Oracle 使用 `1521`，不要同時與其他使用相同埠的環境啟動。

### 方式一：完整容器環境

在根目錄建立不進版控的 `.env`，替換下列占位值。請只用本機練習專用帳密：

```dotenv
ORACLE_DEV_USERNAME=demo_app
ORACLE_DEV_PASSWORD=your_local_db_password
INTERNAL_USERNAME=internal
INTERNAL_PASSWORD=your_local_internal_password
```

啟動應用、Oracle 與監控服務：

```powershell
podman compose up -d --build
podman compose ps
```

首次下載映像與初始化 Oracle 需要時間。等待 `oracle-db` 與 app 就緒，再確認：

```powershell
Invoke-RestMethod http://localhost:8787/actuator/health
```

目前 Compose 會傳入資料庫與 `internal` 機器帳號設定，**沒有把 `.env` 中的 `API_USERNAME`／`API_PASSWORD` 自動傳入 app**。這條啟動方式的外部 API 憑證採 app 容器環境或 [application.yml](src/main/resources/application.yml) 的本機預設；要覆寫必須明確注入 app 容器，不能只在 `.env` 多寫兩行。

### 方式二：只有 Oracle 用容器，後端從 IDE／Gradle 執行

把設定送進啟動後端的同一個 PowerShell 程序；下列密碼須替換，DB 值要與既有開發庫一致：

```powershell
$env:ORACLE_DEV_USERNAME = 'demo_app'
$env:ORACLE_DEV_PASSWORD = '<本機開發資料庫密碼>'
$env:API_USERNAME = 'api'
$env:API_PASSWORD = '<本機 API 機器帳號密碼>'
$env:INTERNAL_USERNAME = 'internal'
$env:INTERNAL_PASSWORD = '<本機 internal 機器帳號密碼>'
$env:OTLP_TRACING_ENABLED = 'false'

podman compose up -d oracle-db
# 等 Oracle healthy 後再啟動應用
./gradlew bootRun
```

IDE 直接執行時，也要將同一組環境變數設在 run configuration。**Compose 讀取 `.env`，不代表 IDE／Spring Boot 會自動讀取它。**

這種方式沒有啟動監控後端，所以關閉 tracing 匯出，避免持續嘗試連線到 Alloy；健康檢查與應用本身的 metrics 仍可使用。Schema 由 Flyway 管理，Hibernate 採 `validate`，不是啟動時任意修改資料表。

暫停容器環境可用 `podman compose stop`；它保留容器與資料。不要把移除 volume 的重置命令當日常停止方式。

### 服務入口

| 入口 | 用途／存取方式 |
| --- | --- |
| <http://localhost:8787/actuator/health> | 健康檢查，免認證；包含 DB 就緒狀態。 |
| <http://localhost:8787/swagger-ui/index.html> | API 瀏覽與操作；文件免認證，呼叫受保護端點時使用 Authorize。 |
| <http://localhost:8787/v3/api-docs> | 執行中的 OpenAPI JSON，免認證。 |
| `/account`、`/product`、`/order` | 業務 API，需要 HTTP Basic；完整路徑看 Swagger。 |
| `/actuator/metrics`、`/actuator/prometheus` | 需要 Basic；Alloy 使用 `internal` 機器帳號抓取 Prometheus 指標。 |
| <http://localhost:3000> | Grafana，僅完整容器環境。 |
| <http://localhost:9090>、<http://localhost:3200> | Prometheus 與 Tempo 查詢入口，僅完整容器環境。 |

## 測試與 API 文件

```powershell
# CI gate 同等的測試選擇；可能啟動 Testcontainers Oracle
./gradlew test "-Djunit.platform.exclude.tags=SanityTest"

# 只執行指定測試，例如 OrderService 的單元測試
./gradlew test --tests "*OrderServiceTest"

# 執行名稱符合的整合測試
./gradlew test --tests "*IntegrationTest"

# 產生 build/docs/swagger.json
./gradlew generateOpenApiDocs
```

- `test` 不只是純單元測試。Oracle 測試需要可用的 container runtime；Windows／Podman 環境與多個 Oracle 搶記憶體的處理見[測試規範](docs/agents/08-testing.md)。
- [BaseIntegrationTest](src/test/java/com/ibm/demo/BaseIntegrationTest.java) 的 Oracle 在整個測試 JVM 內共用，避免跨測試類別重用 Spring context 時連到已被關閉的容器。
- MockMvc 契約測試驗證實際序列化、HTTP status、Location 與 ProblemDetail；Mockito 呼叫次數不能代替這些驗證，也不能代替 DB 交易／鎖測試。
- OpenAPI 生成會啟動專用 profile。執行前確認 `8787` 沒有其他 app，避免抓到殘留程序的規格；H2 文件生成成功也不代表 Oracle 整合測試通過。

## 監控怎麼串

指標與追蹤是兩條不同方向的鏈：

- **Metrics**：Alloy 抓取 app 的 `/actuator/prometheus`，再寫入 Prometheus，由 Grafana 查詢。
- **Traces**：app 經 OTLP 推送到 Alloy，再由 Alloy 轉送 Tempo，由 Grafana 查詢。

OTLP metrics 匯出目前關閉，避免與 Prometheus scrape 重複計數；tracing 仍使用 OpenTelemetry starter。應用匯出層與本機監控後端分開理解，不要因為指標採 scrape 就刪除 tracing 所需依賴。

如何產生流量、查 PromQL、看 trace 與定位空圖，見[監控使用指南](docs/monitoring-usage-guide.md)。

## CI 與下游整合

[image-publish.yml](.github/workflows/image-publish.yml) 監聽 `main` push、以 `main` 為目標的 PR，以及手動觸發：

1. 執行 Gradle 測試 gate；失敗就不繼續建置映像。
2. 建置並推送 GHCR image，附分支／PR、SHA 等標籤；`main` 對應 `latest`。
3. **push main 時**，以 `backend_image_updated` 事件通知 Playwright repo 驗證已發布映像。
4. 同樣只在 push main 時，後續 `generate-docs` job 產生 Swagger，提交到設定的下游 repo。

因此 **push main 不只是備份程式**，還會發布映像、觸發下游與更新另一個 repo。下游位置由 `DEST_USER`／`DEST_REPO` 設定，跨 repo 存取使用 `MY_REPO_TOKEN`；發布自己的環境前需確認這些設定。

Swagger 同步排在 dispatch 之後，下游開始執行時，版控快照可能仍是上一版。Playwright CI 從受測 app 讀取 live spec 產型別，快照差異只作提示。API 契約變更與上下游發布順序見 [Git 工作流程](docs/agents/03-git-workflow.md)。

## 使用範圍與注意事項

- **安全是教學起點**：目前是 stateless Basic、兩個 in-memory 機器帳號，沒有買方資源或角色的細分授權。Account 與 API Credential 是不同概念；`{noop}` 不是人類密碼儲存方案，正式身份整合見[外部 IdP 說明](docs/security-external-idp-migration.md)。
- **環境僅供本機練習**：監控含匿名 Grafana Admin、開發環境會輸出詳細資料；DB 初始化腳本會列印生成 SQL，可能含密碼。不要放真實資料或共用密碼，分享 log 前先遮蔽。
- **不要把庫存原語當完整交易保證**：跨 HTTP 的分段提交與補償仍有故障情境；目前沒有對外保證 exactly-once、全域一致性或特定容量。
- **不是價格歷史帳本**：目前訂單讀取仍會查商品當前資料，沒有保存成交名稱／單價快照。
- **資料演化可追溯**：已套用的 Flyway migration 不直接修改；終止與資料可見性依目前 ADR 運作。

## 延伸閱讀

| 想了解 | 文件 |
| --- | --- |
| 業務名詞與生命週期 | [CONTEXT](CONTEXT.md)、[ADR](docs/adr/) |
| 環境、JDK、profiles、映像探針 | [建置與執行](docs/agents/02-setup.md) |
| Oracle 測試與 Windows／Podman | [測試規範](docs/agents/08-testing.md) |
| 成功／錯誤回應的取捨 | [API 回應契約](docs/api-response-contract-decision.md) |
| 分頁與 Swagger | [分頁指南](docs/pagination-strategies-guide.md)、[OpenAPI 設計](docs/swagger-openapi-design-guide.md) |
| 容錯與監控 | [Resilience4j 設定](docs/resilience4j-configuration-guide.md)、[監控使用指南](docs/monitoring-usage-guide.md) |

本文依目前程式、設定與 workflow 整理；本次文件整理未重新執行應用、整合測試、E2E 或壓測，不代表當前環境已驗證成功。
