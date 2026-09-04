## 監控與可觀測性

### 指標收集鏈路

```
Spring Boot App (OTLP) → Grafana Alloy → Prometheus → Grafana
```

> 操作面（怎麼起堆疊、怎麼看圖、PromQL 怎麼寫、圖是空的怎麼查）見 **[`docs/monitoring-usage-guide.md`](../monitoring-usage-guide.md)**。本文件只描述鏈路結構與端點契約。

### 關鍵端點

- **健康檢查**: `/actuator/health`（亦為映像內建 `HEALTHCHECK` 的探測目標，下游 E2E repo 依賴此健康狀態判斷就緒；契約細節見 [02-setup.md](./02-setup.md#映像內建-healthcheck重要契約)）
- **指標**: `/actuator/metrics`
- **Prometheus scrape 端點**: 無。本專案只引入 `micrometer-registry-otlp`（見 `build.gradle`），未引入 `micrometer-registry-prometheus`，故 `/actuator/prometheus` 端點不存在（無 registry 就不會 materialize，實際回 404；因此 `application.yml` 的 `exposure.include` 也刻意不列 prometheus）。指標一律走 **OTLP push**（app → Alloy），非 Prometheus 主動 scrape。
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

### 指標匯出的依賴前提（**動 `build.gradle` 前必讀**）

OTLP 匯出需要 **兩個** artifact 同時在 classpath 上，缺一個就完全不匯出、**且不會有任何錯誤訊息**：

| artifact | 提供的類別 |
|---|---|
| `io.micrometer:micrometer-registry-otlp` | `io.micrometer.registry.otlp.OtlpMeterRegistry` |
| `org.springframework.boot:spring-boot-opentelemetry` | `org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetryProperties` |

`OtlpMetricsExportAutoConfiguration` 的 `@ConditionalOnClass` 兩者都要（Boot 4 把 actuator 拆成多模組後的結果）。條件不成立時沒有 `OtlpMeterRegistry` bean、沒有推送執行緒，但 `/actuator/metrics` 仍正常回應（那是 composite registry 的 `SimpleMeterRegistry` 在撐），所以從端點完全看不出異常 —— 此坑實際造成指標中斷 54 天。**不要因為「看起來沒用到」而移除 `spring-boot-opentelemetry`。** 診斷階梯見 [`docs/monitoring-usage-guide.md` §6.1](../monitoring-usage-guide.md#61-最惡毒的一種什麼都沒推而且沒有任何錯誤訊息)。

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

### Grafana 佈建（provisioning）

datasource 與 dashboard **不手動在 UI 建**，一律進 git。手動建的東西存在 `grafana-data` volume，volume 一砍就沒了。掛載路徑的來歷、檔案格式的性質、UI 改動的去向見 [`docs/monitoring-usage-guide.md` §7](../monitoring-usage-guide.md#7-佈建檔是怎麼運作的grafana-底下那三個檔案)。

| 檔案 | 掛載到容器 | 內容 |
|---|---|---|
| `grafana/provisioning/datasources/prometheus.yml` | `/etc/grafana/provisioning/datasources`（`:ro`） | Prometheus datasource，`uid: prometheus`（**寫死**，dashboard JSON 以此 uid 引用）、`url: http://prometheus:9090` |
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
