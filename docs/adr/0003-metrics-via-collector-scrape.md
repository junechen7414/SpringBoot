# 指標改由 Alloy scrape，追蹤維持 OTLP push

本專案原本的指標鏈是全程 push：app 以 OTLP 每 10 秒推給 Alloy，Alloy 轉成 Prometheus 命名後 remote_write 進 Prometheus（`docs/monitoring-usage-guide.md` §2.1 記錄的當時決策與代價）。現在決定推翻其中的指標那一半：**Alloy 去 scrape app 的 `/actuator/prometheus`，再 remote_write 給 Prometheus；追蹤維持 OTLP push，不動。**

推翻的原因是全程 push 最大的代價，**失去 `up`**。app 掛了、網路斷了、Alloy 掛了、依賴不齊而靜默不推送，在查詢上全都長成「沒資料」，所以 `monitoring-needs-first.md` §6.1 只能用 `time() - timestamp(...)` 搭配 `absent(...)` 兩句互補的查詢補救，而且閾值要自己拍。改成 scrape 後 `up{job="demo-app"} == 0` 一句話搞定，實測停掉 app 後約 17 秒內變成 0。同時也拿回 `curl /actuator/prometheus` 這種直接問 app 的除錯方式，不必沿鏈路一段段猜。

原本選 push 的理由（簡單、OTLP 的教學價值）大部分仍由追蹤鏈保有，所以這次轉換放棄的不多。

## Considered Options

- **維持全程 push，用 `absent()` 補救**：不用動任何東西，但要永遠背著兩句互補查詢，而且「app 掛了」與「網路斷了」仍然分不出來。
- **讓 Prometheus 直接 scrape app**：最傳統。但 `/actuator/prometheus` 需要 Basic 認證（見下），憑證會散到 Prometheus 設定裡；也放棄了 Alloy 這個集中處理點。
- **讓 Alloy 去 scrape，再 remote_write 給 Prometheus**（採用）：對 app 是 pull（保住 `up`、保住 `curl` 除錯），對後端是 push（保住集中處理點與網路方向）。

## 決策細節

### 1. Registry 只短期並存，最終只留 scrape

- 加入 `io.micrometer:micrometer-registry-prometheus`。
- `micrometer-registry-otlp` 由 `spring-boot-starter-opentelemetry` 帶入，追蹤鏈也需要這個 starter，所以**不移除依賴**，只把 OTLP 指標匯出關掉。
- 並存只用於過渡期的對照，合併前一定要關。理由：兩條線同時進 Prometheus，同一個指標會出現兩組 series（scrape 那組帶 `job`/`instance`，OTLP 那組沒有），`sum by (uri)` 會**靜默地算成兩倍**，不會報錯。檢查方式：`http_server_requests_seconds_count{job=""}` 應該是空的。

### 2. `/actuator/prometheus` 用 Basic 認證（`internal` 帳號），不放行

- health 會放行，是因為 Dockerfile `HEALTHCHECK` 的 `wget` 是下游 E2E 依賴的契約，不方便帶憑證。scraper 是我們自己寫的設定，Alloy 的 `basic_auth` 一段就能處理，所以放行 health 的理由不適用於這裡。
- 這個端點會暴露整份 API 的 uri 樣板、錯誤率、連線池與 bulkhead 狀態，而 8787 有發佈到宿主。`/actuator/metrics` 現在本身就需要認證，只放行 prometheus 會前後不一致。
- 不採用「management 改到另一個 port 再放行」：health 會跟著搬走，等於破壞 `HEALTHCHECK` 契約。
- 密碼用 `{noop}` 逐字比對，每次 scrape 的認證成本可以忽略。
- 用 `internal` 而不是 `api`：scrape 是服務對服務的呼叫，與 `*Client` 自呼叫同一類。目前沒有任何授權規則區分這兩個角色，這裡只是語意上的選擇。

## Consequences

- **`up` 回來了。** 之後加告警，第一條規則直接是 `up{job="demo-app"} == 0`。
- **憑證要在兩邊對上。** `INTERNAL_PASSWORD` 要同時進 app 與 alloy，`docker-compose.yml` 兩邊都從 `.env` 取同一個值。不一致時 `up == 0`、Alloy 收到 401。
- **指標名稱由 Prometheus registry 決定**，與 OTLP 轉譯時不同。實際遇到的一個：GC 最長停頓從 `jvm_gc_pause_max_seconds` 變成 `jvm_gc_pause_seconds_max`，dashboard 的 GC panel 因此改過一次。該值的滾動視窗也不再是 OTLP 時代的 10 秒：實測一次尖峰約撐 2 分鐘才消失（兩次量到 118 秒與 115 秒，條件見 `docs/monitoring-needs-first.md` §6.1 之後的 GC 段落）。
- **OTLP 時代的三個旋鈕消失**：`base-time-unit`（Prometheus registry 固定用秒）、`aggregation-temporality`（固定累積值）、`OTLP_METRICS_ENABLED`（沒有指標推送可關）。如果有人重新打開 OTLP 指標匯出，這三個坑會一起回來。
- **exemplar 多了新的環節，缺任何一環都不報錯、只是沒有小菱形。** Prometheus registry 只在 OpenMetrics 格式的回應裡帶 exemplar，所以 `prometheus.scrape` 要協商 OpenMetrics，`prometheus.remote_write` 要 `send_exemplars = true`。實測 `query_exemplars` 回得到帶 `trace_id` 的結果，Tempo 的 trace 也照樣收得到。
- **依賴不齊的靜默失敗在指標側變得可偵測**：沒有 `micrometer-registry-prometheus` 時端點回 404、`up == 0`，不再是「所有 panel 全空但看起來一切正常」。追蹤側沒有等價的訊號，仍是同一種靜默失敗（`docs/monitoring-usage-guide.md` §6.1）。
- **失去的**：全程 push 對短命 workload 的好處（不需要 service discovery）。本專案 compose 內寫死一個 target，沒有短命 workload；日後真有，需要回頭考慮。
- **這個 ADR 推翻的**是 `monitoring-usage-guide.md` §2.1 當時的「全程 push」結論。§2.1 保留了當時的取捨表，並標明每一項在現在的狀態，因為它同時是為什麼改的證據。
