## 監控與可觀測性

### 收集鏈路

指標與追蹤是**兩條獨立的鏈**，只共用 app 到 Alloy 這一段（同一個 OTLP/HTTP 4318 端點，
由 Alloy 的 `output` 分流到不同下游）：

```
指標  Spring Boot App --OTLP/HTTP--> Alloy --remote_write--> Prometheus --> Grafana
追蹤  Spring Boot App --OTLP/HTTP--> Alloy --OTLP/gRPC-----> Tempo      --> Grafana
```

「獨立」的實際意義：關掉一條不影響另一條，某一條沒資料也**不能**用來推論另一條的狀態。
app 只認得 Alloy 一個出口 —— Prometheus 與 Tempo 的 receiver 埠都刻意不對宿主發佈。

> 操作面（怎麼起堆疊、怎麼看圖、PromQL 怎麼寫、圖是空的怎麼查）見 **[`docs/monitoring-usage-guide.md`](../monitoring-usage-guide.md)**。本文件只描述鏈路結構與端點契約。

### 關鍵端點

- **健康檢查**: `/actuator/health`（亦為映像內建 `HEALTHCHECK` 的探測目標，下游 E2E repo 依賴此健康狀態判斷就緒；契約細節見 [02-setup.md](./02-setup.md#映像內建-healthcheck重要契約)）
- **指標**: `/actuator/metrics`
- **Prometheus scrape 端點**: 無。本專案只引入 `micrometer-registry-otlp`（由 `spring-boot-starter-opentelemetry` 帶入，見 `build.gradle`），未引入 `micrometer-registry-prometheus`，故 `/actuator/prometheus` 端點不存在（無 registry 就不會 materialize，實際回 404；因此 `application.yml` 的 `exposure.include` 也刻意不列 prometheus）。指標一律走 **OTLP push**（app → Alloy），非 Prometheus 主動 scrape。
- Prometheus 這一端也不 scrape 任何 target：`prometheus.yml` 的 `scrape_configs` 為空，它是**被推**的一方。接收 remote_write 靠的是 `docker-compose.yml` 裡的 `--web.enable-remote-write-receiver` CLI flag，**不是**設定檔裡的 `remote_write:` 區塊（那個區塊語意相反，是把資料推出去）。
- **此選擇的代價**：沒有 scrape 就沒有 Prometheus 自動產生的 `up` 時間序列，因此「app 掛了」與「網路斷了」「Alloy 掛了」「依賴不齊靜默不推」在查詢層面無法區分（都是沒資料）。要偵測服務存活只能用 `absent()` / `time() - timestamp(...)`。設計告警規則前先讀 [`docs/monitoring-usage-guide.md` §2.1](../monitoring-usage-guide.md#21-push-與-scrape-的取捨這個代價值不值得)。

### 健康檢查（HEALTHCHECK）的運作機制

映像的 `HEALTHCHECK`（見 `Dockerfile`）**不是建置指令**，而是烙進 image 的 runtime metadata；容器跑起來後才由容器 runtime 反覆執行。

**三個對象（誰、在哪、扮演什麼）**

| 對象 | 位置 | 角色 |
|------|------|------|
| 容器 runtime（Podman / Docker） | 主機上，容器**外** | 監工：建容器、定時檢查、判定 healthy/unhealthy。**不對 app 發 HTTP** |
| Spring Boot app | 容器**內**，聽 8787 | HTTP **server** |
| wget | 容器**內**（runtime 派出的探針行程） | HTTP **client** |

**流程**

1. runtime 讀 image 的 HEALTHCHECK 設定（每 30s、timeout 30s、啟動 60s 後開始、連續失敗 3 次判 unhealthy）。
2. 每次 runtime 在容器內生一個 wget 行程，執行 `wget --spider http://localhost:8787/actuator/health`。
3. wget 對**同一容器內**的 app 發 HTTP（`localhost` = 容器自身的網路命名空間）。
4. wget 依結果給退出碼（0 成功／非 0 失敗）。
5. runtime **只看退出碼**（看不到 HTTP 內容），據此更新容器健康狀態。

也就是說：runtime 是「觸發者/裁判」，wget 才是「實際發 HTTP 的 client」，app 是 server。runtime 與 Spring Boot **不是** HTTP 的 client/server 關係，而是「監工 → 被監管的行程」。

**與 Spring Security 的關係（重要）**

wget 的請求跟外部請求走**同一條** servlet filter chain。若 `/actuator/health` 未 `permitAll`，wget 無憑證 → 回 **401** → 退出碼非 0 → 連續失敗 → 容器被標 **unhealthy**。因此 `SecurityConfig` 明確放行 `/actuator/health`（見 [06-architecture.md](./06-architecture.md) 安全段）。

### 匯出的依賴前提（**動 `build.gradle` 前必讀**）

這一區的失敗模式只有一種，而且是最難查的一種：**依賴少一個就完全不匯出，且沒有任何錯誤
訊息**。指標那條路曾為此中斷 54 天 —— 因為 `/actuator/metrics` 照樣正常回應（那是 composite
registry 裡的 `SimpleMeterRegistry` 在撐），從端點完全看不出異常。

Boot 4 把 actuator 拆成多個模組，湊齊這組 artifact 的難度比 Boot 3 高得多：

| 鏈路 | autoconfiguration | `@ConditionalOnClass` 要求的類別（來自幾個 artifact） |
|---|---|---|
| 指標 | `OtlpMetricsExportAutoConfiguration` | `OtlpMeterRegistry`、`OpenTelemetryProperties` —— **2** 個 artifact |
| 追蹤 | `OtlpTracingAutoConfiguration` | `OtelTracer`、`OpenTelemetry`、`OtlpHttpSpanExporter`、`SdkTracerProvider` —— 4 個類別散在 **3** 個 artifact |

所以本專案**不逐條列 artifact**，改為引入單一 starter：

```gradle
implementation 'org.springframework.boot:spring-boot-starter-opentelemetry'
```

它一次帶進七個 entry（`spring-boot-micrometer-tracing-opentelemetry`（追蹤的
autoconfiguration 在這裡，不在 `spring-boot-actuator-autoconfigure`）、`spring-boot-opentelemetry`、
`spring-boot-starter-micrometer-metrics`、`micrometer-registry-otlp`、`micrometer-tracing-bridge-otel`、
`opentelemetry-exporter-otlp` 等），兩條鏈的條件同時成立。

**規則**：不要把這個 starter 換回手列 artifact，也不要因為「看起來沒用到」而移除其中任何
一項傳遞依賴。手列很容易只中三條而毫無徵兆 —— 那正是 54 天那次的成因。要驗證條件是否
成立，唯一可靠的方式是實際檢查 classpath：

```bash
./gradlew dependencies --configuration runtimeClasspath
```

診斷階梯見 [`docs/monitoring-usage-guide.md` §6.1](../monitoring-usage-guide.md#61-最惡毒的一種什麼都沒推而且沒有任何錯誤訊息)。

### 指標匯出設定（改動前務必了解）

設定在 `application-dev.yml`（base `application.yml` 的 `management.otlp.metrics.export.enabled` 預設 **false**，只有 dev profile 打開，讓 `integration-test` / `openapi` 不會嘗試推送；`e2e` 預設也是關的，下游若補上 alloy 服務可用 `OTLP_METRICS_ENABLED=true` 打開）。

| 設定 | 值 | 為什麼不能省 |
|---|---|---|
| `management.otlp.metrics.export.base-time-unit` | `seconds` | Micrometer OTLP **預設 milliseconds** → 指標名會變成 `..._milliseconds_...`、值為毫秒，與 Prometheus 慣例、所有 PromQL 範例、Grafana 的 `s` 單位全部不合 |
| `management.metrics.distribution.percentiles-histogram` | `http.server.requests: true` | 不開的話 OTLP 只送 `count`/`sum`、**沒有 `_bucket`** → `histogram_quantile()` 一律回空值，p99 查不到 |
| `management.otlp.metrics.export.aggregation-temporality` | **不設**（預設 `cumulative`） | Prometheus 的 `rate()` 依賴單調遞增的累積計數；改成 `delta` 會讓所有 `rate()` 查詢失效 |

環境變數（皆為選填，不設就用 `application-dev.yml` 的預設）：

| 變數 | 預設 | 用途 |
|---|---|---|
| `ALLOY_HOST` | `localhost` | OTLP 目標主機名。`docker-compose.yml` 為 `app` 注入 `alloy`；本機 IDE/`bootRun` 省略即可。與 `ORACLE_DB_HOST` 同一慣例 —— **只有主機名因執行環境而異**，URL 格式寫在設定檔 |
| `OTLP_METRICS_ENABLED` | `true` | 設 `false` 可在本機不起監控堆疊時關掉推送，避免每個 `step`（10s）噴一次連線失敗 |

### 追蹤（tracing）設定（改動前務必了解）

設定在 `application-dev.yml`。其他 profile **不需要**另外關掉 —— 理由見下面「exporter 綁在
endpoint 上」。

| 設定 | 值 | 為什麼 |
|---|---|---|
| `management.opentelemetry.tracing.export.otlp.endpoint` | `http://${ALLOY_HOST:localhost}:4318/v1/traces` | 與指標同一個 Alloy、同一個 4318 埠，只有 path 不同 |
| `management.tracing.sampling.probability` | `1.0` | **預設是 0.1**。單機 demo 上 10% 採樣會變成「打了 API 卻查不到 trace」的常態，很容易誤判成鏈路沒接通。正式環境請往下調 —— trace 量與儲存成本成正比 |
| `management.tracing.export.otlp.enabled` | `${OTLP_TRACING_ENABLED:true}` | 本機不起監控堆疊時設 `false`，免得 batch span processor 每 5 秒噴一次連線失敗。由 `@ConditionalOnEnabledTracingExport` 消化 |

#### 三個容易踩的點

**1. 屬性名不對稱。** 指標與追蹤的 namespace 長得不一樣，靠記憶類推一定寫錯：

| | 屬性 |
|---|---|
| 指標端點 | `management.otlp.metrics.export.url` |
| 追蹤端點 | `management.opentelemetry.tracing.export.otlp.endpoint` |

**2. Boot 3.x 的舊屬性名在 Boot 4 是 `deprecation level: error`。** 寫舊名**不是**靜默失效，
而是**直接啟動失敗**（這點與依賴缺失的失敗模式恰好相反）：

| Boot 3.x（已失效） | Boot 4 |
|---|---|
| `management.otlp.tracing.endpoint` | `management.opentelemetry.tracing.export.otlp.endpoint` |
| `management.otlp.tracing.export.enabled` | `management.tracing.export.otlp.enabled` |
| `management.tracing.opentelemetry.export.*` | `management.opentelemetry.tracing.export.*` |

**3. exporter 綁在 endpoint 屬性上。** `OtlpTracingConfigurations$ConnectionDetails` 帶
`@ConditionalOnProperty(...export.otlp.endpoint)`，而 `$Exporters` 是
`@ConditionalOnBean(OtlpTracingConnectionDetails)`。**不設 endpoint → 沒有 exporter bean →
不會嘗試連線、也不會有錯誤**。因此 `integration-test`／`openapi`／`e2e` 都不必寫
`enabled: false`（指標那條路需要，因為 registry 的條件不看 URL）。`@SpringBootTest` 另有一層
保護：Boot 預設不在測試中自動配置匯出元件。

環境變數：

| 變數 | 預設 | 用途 |
|---|---|---|
| `ALLOY_HOST` | `localhost` | 與指標**共用**同一個變數，不另設一個 |
| `OTLP_TRACING_ENABLED` | `true` | 與 `OTLP_METRICS_ENABLED` 用途相同但彼此獨立 |

#### 兩個副作用（都是預期行為）

- **log 會多出關聯欄位。** 追蹤一進 classpath，Boot 預設的 correlation pattern 就會在每行 log
  插入 `[demo,traceId,spanId]`（`demo` 來自 `spring.application.name`）。沒有 trace context 時
  那格是空白（例如啟動與 shutdown hook 的訊息）。本專案沒有自訂 `logging.pattern.*`，所以是
  Boot 的預設格式在生效 —— 看到 log 變寬不是壞掉。
- **OTLP 指標會開始帶 exemplars。** `OtlpExemplarsAutoConfiguration` 是
  `@ConditionalOnBean(io.micrometer.tracing.Tracer)`，一旦有 Tracer 就生效
  （`management.tracing.exemplars.include` 預設 `sampled-traces`）。但**應用端產生只是三分之一**
  —— 要在 Grafana 的指標圖上點小菱形跳進 trace，這三處必須同時成立，缺任何一處都**不會報錯**，
  症狀一律是「沒有小菱形」：

  | 環節 | 設定 | 缺了會怎樣 |
  |---|---|---|
  | 應用產生 | 有 Tracer bean 即自動生效（無需設定） | OTLP 指標裡沒有 exemplar |
  | Prometheus 儲存 | `docker-compose.yml` 的 `--enable-feature=exemplar-storage` | remote write 收下後直接丟掉，`/api/v1/query_exemplars` 永遠回空陣列 |
  | Grafana 連結 | `grafana/provisioning/datasources/prometheus.yml` 的 `exemplarTraceIdDestinations`（`name: trace_id`、`datasourceUid: tempo`） | exemplar 有存但 Grafana 不知道要跳去哪 |

  label 名是 `trace_id`／`span_id`（由 Alloy 的 `otelcol.exporter.prometheus` 從 OTel exemplar
  轉出），不是 `traceID` 或 `traceId`；寫錯同樣是靜默失效。驗證方式：
  `curl --get 'http://localhost:9090/api/v1/query_exemplars' --data-urlencode 'query=http_server_requests_seconds_bucket' --data-urlencode 'start=...' --data-urlencode 'end=...'`
  —— 回傳的 `labels` 裡要看得到 `trace_id`。

#### 鏈路兩端的設定檔

| 檔案 | 角色 |
|---|---|
| `config.alloy` | 4318 receiver 的 `output` 多一行 `traces`，經 `otelcol.processor.batch "traces"` 後由 `otelcol.exporter.otlp "tempo"` 送到 `tempo:4317`（該 exporter **只講 gRPC**，且同網段明文連線必須設 `insecure = true`）。span 是突發性的（一個請求可能一次十幾個），所以只有 traces 需要 batch processor —— 指標由 Micrometer 自己按 step 批次推 |
| `tempo.yaml` | Tempo 單體（monolithic，`target: all`）模式：`stream_over_http_enabled: true`（TraceQL 編輯器需要）、`storage.trace.backend: local`（生產請換物件儲存）、`backend_scheduler.provider.compaction.compaction.block_retention: 24h`（預設 336h）。**這份設定綁 Tempo 3.x** —— 3.0 換掉內部架構，`ingester` → `live_store`＋`block_builder`、`compactor` → `backend_scheduler`／`backend_worker`，網路上的 2.x 範例會直接 `failed parsing config: field ingester not found` 起不來。要查某欄位在當前版本叫什麼：`curl localhost:3200/status/config` |
| `grafana/provisioning/datasources/prometheus.yml` | `exemplarTraceIdDestinations` —— exemplar 連結的 Grafana 端，見上面的三環節表 |
| `docker-compose.yml` | `prometheus` 的 command 要有 `--enable-feature=exemplar-storage`（exemplar 儲存的必要條件）。`tempo` 服務只發佈查詢埠 `3200`；OTLP 的 4317/4318 刻意不發佈（宿主的 4318 是 Alloy 在用）。入口參數**必須**帶 `-config.file=/etc/tempo/tempo.yaml`，少了它 Tempo 會用內建預設值（**沒有 OTLP receiver**）而自己看起來仍然健康。`alloy` 加了 `depends_on: tempo`，否則 Alloy 先起會持續噴 export 失敗 |

### Grafana 佈建（provisioning）

datasource 與 dashboard **不手動在 UI 建**，一律進 git。手動建的東西存在 `grafana-data` volume，volume 一砍就沒了。掛載路徑的來歷、檔案格式的性質、UI 改動的去向見 [`docs/monitoring-usage-guide.md` §7](../monitoring-usage-guide.md#7-佈建檔是怎麼運作的grafana-底下那三個檔案)。

| 檔案 | 掛載到容器 | 內容 |
|---|---|---|
| `grafana/provisioning/datasources/prometheus.yml` | `/etc/grafana/provisioning/datasources`（`:ro`） | Prometheus datasource，`uid: prometheus`（**寫死**，dashboard JSON 以此 uid 引用）、`url: http://prometheus:9090` |
| `grafana/provisioning/datasources/tempo.yml` | `/etc/grafana/provisioning/datasources`（`:ro`） | Tempo datasource，`uid: tempo`（**寫死**，同 prometheus 的理由）、`url: http://tempo:3200`（查詢埠，不是 OTLP 的 4317/4318）。刻意不設 `tracesToMetrics` / `serviceMap` —— 那兩個依賴 span metrics 與 service graph 指標，需要在 Alloy 加 `otelcol.connector.spanmetrics` / `servicegraph` 才有，現在設了只會得到空面板 |
| `grafana/provisioning/dashboards/default.yml` | `/etc/grafana/provisioning/dashboards`（`:ro`） | dashboard provider，指向 `/etc/grafana/dashboards`，`updateIntervalSeconds: 10` |
| `grafana/dashboards/app-overview.json` | `/etc/grafana/dashboards`（`:ro`） | 四個黃金訊號的入門 dashboard（7 panel）。每個 panel 的 `description` 寫該句 PromQL 的推導理由 —— 這份 dashboard 兼作教材，新增 panel 時請維持此慣例 |

改動後的生效方式**不同**：

- 改 `grafana/dashboards/*.json` → provider 每 10 秒重掃，**重新整理瀏覽器**即可
- 改 `grafana/provisioning/**` → 需 `podman compose restart grafana`

### Resilience4j 監控指標

- `resilience4j.circuitbreaker.state`: 熔斷器狀態
- `resilience4j.bulkhead.available.concurrent.calls`: 可用許可數
- `resilience4j.circuitbreaker.failure.rate`: 失敗率

### CI/CD 流程

#### GitHub Actions Workflow

1. **單元測試**: 執行 `./gradlew test` 作為 Quality Gate（排除 SanityTest）
2. **Docker 建置**: 多階段建置，僅在容器內執行 `bootJar`（跳過測試）
3. **映像檔推送**: 推送至 GitHub Container Registry (GHCR)
4. **觸發 E2E**: 透過 `repository_dispatch` 通知 E2E 測試專案（`build-and-push` 的**最後一步**）
5. **文件生成** (獨立 Job，`needs: build-and-push`，僅 push `main` 時執行):
   - 執行 `./gradlew generateOpenApiDocs` 產生 `swagger.json`
   - Checkout 目標 repo（保留現有文件）
   - 僅複製 `swagger.json` 至目標 repo 的 `docs/` 目錄
   - Commit and push（使用 checkout+copy 方式，避免覆蓋目標 repo 其他文件）

#### 步驟 4 與 5 的順序：下游快照必定落後一版（**預期行為**）

dispatch 在步驟 4 就發出，而推快照的步驟 5 是 `needs: build-and-push`，因此**下游 E2E 的
checkout 永遠早於快照 commit**。2026-08-13（`b09b76a`）實測：

| UTC | 事件 |
|---|---|
| 11:52:36 | 下游 run #180 開始、checkout → 拿到 8/07 的舊 `docs/swagger.json` |
| 11:53:39 | `generate-docs` 才把新快照推進下游（**慢 63 秒**，+90/−39） |

後果：**上游只要改了 API 契約，下游 job summary 就一定出現一次「快照與被測 image 的 spec
有差異」**。它不阻擋測試（下游型別改由被測容器的 live spec 產生，見下游
`docs/agents/13-advanced-techniques.md`），也不需要手動補快照 —— 下一次自動觸發即恢復 ✅。

真正的異常只有一種：**連續多次 push `main` 都出現相同差異** → `generate-docs` 沒推成功
（token 過期、job 失敗），去查上游 run。另外上游 PR 貼標籤觸發的 E2E 因為步驟 5 不執行，
那個 image 的 spec **永遠**沒有對應快照，差異持續到 PR 合併為止，同樣正常。

> 想讓「有差異」恢復成真正的異常訊號，就得把 dispatch 拆成 `needs: [build-and-push,
> generate-docs]` 的獨立 job；代價是 E2E 晚約 1 分鐘起跑。目前刻意不做，改為在兩邊文件與
> 下游 summary 訊息把語意寫清楚。

#### 快取策略

- Gradle 依賴快取: `actions/setup-java` 的 `cache: gradle`
- Docker Layer 快取: `type=gha,scope=${{ github.ref_name }}`
