# 01 — 指標改由 Alloy scrape `/actuator/prometheus`

Status: ready-for-agent

Spec：[../spec.md](../spec.md)

開工前先讀 `docs/agents/09-monitoring.md`，改動範圍涵蓋它列出的必讀檔案。走 branch + PR（`high-risk-pr-workflow` skill）：監控斷線測試抓不到，屬於 `03-git-workflow.md` 說的「測試抓不到、壞了影響大」。

## 步驟

### App 端

- **`build.gradle`**：在可觀測性段落加 `io.micrometer:micrometer-registry-prometheus`。註解要說明為什麼 `micrometer-registry-otlp` 還在 classpath 卻不匯出（由追蹤鏈需要的 starter 帶入）。
- **`src/main/resources/application.yml`**：`management.endpoints.web.exposure.include` 加入 `prometheus`，並刪掉那段「不含 prometheus」的註解。
- **`src/main/resources/application-dev.yml`**：移除 `management.otlp.metrics.export.*`，或改成 `enabled: false` 並加註解說明原因（擇一，以讀起來清楚為準）。`base-time-unit: seconds` 那段說明要一併處理：Prometheus registry 本身就固定用秒。
  - 確認 `management.metrics.distribution.percentiles-histogram` 與 `management.metrics.tags.application`（第 71 行附近）在 Prometheus registry 下仍然生效。
- **`ALLOY_HOST` / `OTLP_METRICS_ENABLED`**：改成只剩追蹤在用，`OTLP_METRICS_ENABLED` 可能要整個刪掉。記得同步更新 `docker-compose.yml` 的 env 註解與 `docs/agents/02-setup.md`。
- **`config/SecurityConfig.java`**：授權規則不改，`anyRequest().authenticated()` 已經涵蓋。只要在類別 Javadoc 補一句「`/actuator/prometheus` 刻意不放行，由 Alloy 帶 `internal` 憑證 scrape」，並附上 spec 決策 2 的連結。

### Collector 端

- **`config.alloy`**：
  - 新增 `prometheus.scrape "app"`：`targets = [{"__address__" = "app:8787"}]`、`metrics_path = "/actuator/prometheus"`，`basic_auth` 的帳號為 `internal`、密碼用 `sys.env("INTERNAL_PASSWORD")`，`forward_to = [prometheus.remote_write.local.receiver]`。`job_name` 要明確設定，因為 dashboard 和告警會依賴它。
  - 刪除 `otelcol.exporter.prometheus "default"`，以及 `otelcol.receiver.otlp` 的 `metrics` output（receiver 留著給 traces）。
  - 順手把檔案最上方那幾段已經過時的中文註解改正確。
- **`docker-compose.yml`**：alloy 已經同時在 `backend-net` 和 `monitor-net`，連得到 `app:8787`，網路不用改。要替 alloy 注入 `INTERNAL_PASSWORD`，值和 app 用同一個來源，而且不要寫死在 compose 裡（照 `.env` 的慣例，`.env.example` 也要同步）。
  - `backend-net` 上方那句註解「app only pushes data to alloy」要改寫。

### 坑：exemplar 會靜默消失

guide §9.3 的 exemplar 三環鏈，在換成 scrape 之後會多出新的環節，而且缺任何一環都**不會報錯**：

- Prometheus registry 只會在 **OpenMetrics** 格式的回應裡帶 exemplar（要用 `Accept: application/openmetrics-text` 請求才拿得到）
- `prometheus.scrape` 要設定成以 OpenMetrics 格式抓取並保留 exemplar（查 Alloy 文件，例如 `scrape_protocols` 等相關參數）
- `prometheus.remote_write` 的 endpoint 要設 `send_exemplars = true`
- Prometheus 端的 `--enable-feature=exemplar-storage` 與 Grafana 的 `exemplarTraceIdDestinations` 照舊

### Dashboard

- **`grafana/dashboards/app-overview.json`**：先用 guide §3 步驟 3 列出**實際查到的**指標名，再逐一核對每個 expr，不要用猜的。已知差異：OTLP 轉譯出的 `jvm_gc_pause_max_seconds`，改成 scrape 後會是 `jvm_gc_pause_seconds_max`（見 `monitoring-needs-first.md` 中提到這個名稱的段落）。
- 檢查有沒有 panel 依賴 OTLP 才有的 label（例如 resource attributes）。scrape 會新增 `job`/`instance` 兩個 label。

### 文件

- `docs/monitoring-usage-guide.md`：§2 管線圖與 hop 表、§2.1（改寫成「曾經是 push，現在指標改走第三條路」，保留取捨表的教學內容）、§6 決策樹（新增「先 `curl /actuator/prometheus`」這一步、`up` 的判讀）、§10 告警列
- `docs/monitoring-needs-first.md` §6.1：改成以 `up == 0` 為主，原本那兩句互補查詢降級為「push 模式下的做法」並保留實測表
- `docs/agents/09-monitoring.md`：「Prometheus scrape 端點：無」那段，以及鏈路描述
- `docs/agents/01-overview.md` 的「監控鏈路」那行
- `筆記.md` 監控段落（依 memory 慣例只修具體不符的地方）。另外第 658 行的「五個服務」已經過時，實際是六個，含 tempo
- 新增 `docs/adr/0003-metrics-via-collector-scrape.md`
- 改了 `docs/agents/*` 之後，用 `ai-doc-sync` 跑一次 dry-run

## 驗收條件

- [ ] 未帶憑證：`curl localhost:8787/actuator/prometheus` → 401；帶 `internal` 憑證 → 200，內容含 `http_server_requests_seconds_bucket`
- [ ] Prometheus 查得到 `up{job="<job_name>"} == 1`；`podman compose stop app` 之後一個 scrape 週期內變成 `0`
- [ ] Prometheus 裡沒有重複的 series：`count by (__name__) ({__name__="http_server_requests_seconds_count", job=""})` 為空，代表 OTLP 指標匯出確實關閉了
- [ ] Grafana App Overview 七個 panel 都有資料
- [ ] `query_exemplars`（guide §9.3 的指令）回傳的結果含 `trace_id`
- [ ] Tempo 照樣收得到 trace（guide §9.1），證明追蹤鏈沒受影響
- [ ] `./gradlew test "-Djunit.platform.exclude.tags=SanityTest"` 全綠

## Comments
