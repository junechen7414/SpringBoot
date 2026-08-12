# 監控使用指南

> 這份文件是給**人**讀的操作指南：怎麼把監控堆疊跑起來、怎麼看圖、圖是空的時候怎麼查。
> 若你要找的是「這條鏈路怎麼接的」這類架構描述，見 [`docs/agents/09-monitoring.md`](./agents/09-monitoring.md)。

讀完你應該能自己做到：起堆疊 → 產生流量 → 在 Grafana 看到四個黃金訊號 → 在 Prometheus UI 手打一句 PromQL 驗證 → 圖是空的時候依決策樹定位斷在哪一段 → 看懂 `grafana/` 底下的佈建檔在做什麼 → 自己加一個 panel。

---

## 1. 先分清三根柱子：metrics / traces / logs

三者不是同一件事的三種寫法，而是**成本結構不同**、回答不同問題的三種資料。搞混會做出又貴又難查的系統。

| | 回答什麼問題 | 取樣 | 儲存成本 | 本專案 |
|---|---|---|---|---|
| **Metrics（指標）** | 「有多少 / 多快 / 多滿」 | 100% 流量 | **最便宜** — 只存聚合後的數字，不存個別請求 | ✅ 已有（本文件主題） |
| **Traces（追蹤）** | 「**這一筆**請求的時間花在哪一段」 | 通常抽樣（1%～10%） | 中等 — 每筆請求存一棵 span 樹 | ❌ 尚未（見 §9） |
| **Logs（日誌）** | 「發生了什麼**離散事件**」 | 100% | **最貴** — 每筆一整行字串，無法聚合 | ✅ 已有（`log.info` 業務事件） |

**關鍵在「metrics 存的是聚合值」**。一萬個請求進來，`http.server.requests` 這個指標不會存一萬筆紀錄，它只維護幾個累加的數字（總次數、總耗時、各個耗時區間的落點數）。所以 metrics 可以 100% 覆蓋還很便宜——代價是你**永遠問不出「是哪一筆請求慢」**，只能問「慢的那 1% 有多慢」。要追到個別請求就得靠 traces。

這也解釋了為什麼先前那支 `LoggingAspect`（用 AOP 印每個方法的進出與 `Execution time: 12ms`）該刪：

- 「量測耗時」是 metrics 的工作 —— 而且 `http.server.requests` 已經免費做了，還做得更好（有分位數，不是一堆散落的單點）。
- 「方法級別的呼叫鏈」是 traces 的工作。
- 用 log 做這兩件事，等於選了最貴的柱子去做另外兩根柱子的事。

**但 AOP 本身沒有錯**——Micrometer 的 `@Timed` / `@Observed` 就是 AOP 實作的。差別是它把攔截到的東西送進 metrics/traces（便宜、可聚合），而不是印成字串（貴、只能搜尋）。所以「AOP 的正當用途在哪裡」的答案是：**在 metrics 與 tracing，不在 log。**

---

## 2. 本專案的管線：push，不是 scrape

```
Spring Boot App          Grafana Alloy              Prometheus            Grafana
(micrometer-otlp)  --->  (otelcol receiver)  --->   (TSDB)        <---    (查詢並畫圖)
    每 10s 主動推          轉譯格式後 remote_write      被動接收              PromQL
       OTLP/HTTP :4318         :9090/api/v1/write      :9090               :3000
```

四段各自的責任與「壞掉時的症狀」：

| Hop | 誰對誰做什麼 | 壞掉的症狀 |
|---|---|---|
| 1. app → Alloy | app 每 `step`（10s）把指標 **POST** 到 `http://${ALLOY_HOST}:4318/v1/metrics` | app log 出現連線失敗；Prometheus 查不到任何 `http_server_*` |
| 2. Alloy 內部 | `otelcol.receiver.otlp` 收下 → `otelcol.exporter.prometheus` 轉成 Prometheus 命名（`.` → `_`，補單位後綴）→ `prometheus.remote_write` 推出去 | Prometheus 有連線但指標名跟你查的不一樣（見 §6 決策樹第 2 步） |
| 3. Alloy → Prometheus | remote_write 推進 `http://prometheus:9090/api/v1/write` | Prometheus 完全沒資料 |
| 4. Grafana → Prometheus | Grafana 拿 PromQL 去查 `http://prometheus:9090` | Prometheus UI 查得到、Grafana panel 卻空白 → datasource 設定問題 |

### 為什麼是 push 而不是 scrape（很多教材預設 scrape，會對不上）

多數 Prometheus 教材的模型是：Prometheus 定時去每個 app 的 `/actuator/prometheus` **抓**（scrape）。本專案**不是**這樣：

- `build.gradle` 只引入 `micrometer-registry-otlp`，**沒有** `micrometer-registry-prometheus`。沒有那個 registry，`/actuator/prometheus` 端點就不會存在（實際打會回 **404**）。這是刻意的，不是漏掉。
- 因此 `prometheus.yml` 的 `scrape_configs` 是**空的**，Prometheus 在這條鏈裡是**被推**的一方。

**踩過的坑**：讓 Prometheus 能「接收」remote_write，靠的是 `docker-compose.yml` 裡的 CLI flag `--web.enable-remote-write-receiver`，**不是**設定檔裡的 `remote_write:` 區塊。那個區塊語意剛好相反——它是叫 Prometheus 把自己的資料推**出去**給別的後端。舊版 `prometheus.yml` 曾經把自己的位址填進去（等於推給自己），註解還寫成「開啟接收功能」，兩者都是錯的。

> push 的取捨：app 不必開放端點被連進來（安全面較好、也適合會跑掉的短命實例），代價是 app 必須知道 collector 在哪、且 collector 掛了指標會直接掉。

---

## 3. 從零開始：五分鐘看到圖

> 以下指令以 **PowerShell** 為準（`;` 串接、`./gradlew`）。容器一律 `podman`。

### 步驟 1：起堆疊

需要 repo 根目錄有 `.env`（內含 `ORACLE_DEV_USERNAME` / `ORACLE_DEV_PASSWORD`，範本見 `.env.example`）。

```powershell
podman compose up -d
podman compose ps          # 等 oracle-db 變成 healthy（首次啟動可能要 2～3 分鐘）
```

起來的五個容器：`spring-boot-app`(8787)、`oracle-db`(1521)、`grafana-alloy`(4318)、`prometheus`(9090)、`grafana`(3000)。

### 步驟 2：產生流量（沒有流量就沒有圖）

指標是「有請求才會動」的。全新啟動的系統打開 Grafana 只會看到空圖——**這不是壞掉**。先打幾個請求：

```powershell
$cred = 'api:local-api-secret'
$auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($cred)) }

# 成功流量（跑幾輪讓 rate() 有東西可算）
1..30 | ForEach-Object {
  Invoke-RestMethod -Uri 'http://localhost:8787/product' -Headers $auth | Out-Null
  Invoke-RestMethod -Uri 'http://localhost:8787/account' -Headers $auth | Out-Null
}

# 故意製造一個 404，讓錯誤率 panel 有東西
try { Invoke-RestMethod -Uri 'http://localhost:8787/account/999999' -Headers $auth } catch { $_.Exception.Response.StatusCode }
```

注意路徑**沒有** `/api` 前綴。帳密 `api` / `local-api-secret` 是 `application.yml` 的本機預設值（正式環境以 env 覆蓋）。

推送間隔是 10 秒，所以**打完請求最多等 10～20 秒**指標才會出現在 Prometheus。

### 步驟 3：先在 Prometheus UI 確認資料真的進去了

開 <http://localhost:9090>，這一步比直接開 Grafana 重要——它把「資料有沒有進來」跟「圖有沒有畫對」拆成兩個獨立問題。

先看有哪些指標名：

```powershell
(Invoke-RestMethod 'http://localhost:9090/api/v1/label/__name__/values').data | Where-Object { $_ -like 'http_server*' }
```

應該看到 `http_server_requests_seconds_count` / `_sum` / `_bucket`。**若後綴跟這裡寫的不一樣，以實際查到的為準**（Alloy 的命名轉譯會依指標單位與型別補後綴），並照 §8 修正 dashboard 的 PromQL。

然後在 Prometheus 的 Graph 頁貼這句，確認 histogram bucket 真的有送出來：

```promql
histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
```

**有數值** = `percentiles-histogram` 設定生效了。回空值 = 只有 count/sum、沒有 bucket（見 §6）。

### 步驟 4：Grafana

開 <http://localhost:3000>（匿名 Admin，不用登入）。左側 **Dashboards** 裡應該已經有 **App Overview**——datasource 與 dashboard 都是佈建檔自動建的，不需要手動加。

七個 panel 都有 `description`（標題旁的 ⓘ 圖示），寫的是**那句 PromQL 為什麼這樣寫**。那些說明是這份指南的延伸，建議一個一個滑過去讀。

---

## 4. PromQL 最小夠用集

只要四個概念就能讀懂 dashboard 裡全部的查詢。

### 4.1 先分清 counter 與 gauge（決定要不要包 `rate()`）

| 型別 | 意義 | 例子 | 怎麼查 |
|---|---|---|---|
| **Counter** | **只增不減**的累積總量 | `http_server_requests_seconds_count`、`hikaricp_connections_timeout_total` | **幾乎一定要包 `rate()`** |
| **Gauge** | 當下的瞬時值，可上可下 | `hikaricp_connections_pending`、`jvm_memory_used_bytes`、`resilience4j_bulkhead_available_concurrent_calls` | **直接查**，不要包 `rate()` |

counter 直接畫出來是一條**只會往上的斜線**——它是「開機至今的總數」，看不出現在忙不忙。

### 4.2 `rate()`：把累積值換算成速率

```promql
rate(http_server_requests_seconds_count[5m])
```

「過去 5 分鐘內，平均每秒增加多少」。`[5m]` 是計算斜率的時間窗：窗越大越平滑但對尖峰越鈍，窗越小越靈敏但越抖。`rate()` 還會自動處理 app 重啟導致計數器歸零的情況。

> 這也是為什麼**錯誤率必須「先 rate 再相除」**。若直接把兩個累積計數器相除，得到的是「開機至今的平均錯誤率」——剛剛爆掉的那五分鐘會被幾小時的正常流量稀釋到看不見。

### 4.3 `sum by (...)`：決定「一條線代表什麼」

一個指標會因為標籤組合（`uri` × `method` × `status` × `outcome` × …）拆成很多條序列。`sum by (uri)` 表示「只保留 `uri` 這個標籤，其餘全部加總掉」，於是一條線 = 一個端點。

`uri` 標籤是 Spring 的**樣板路徑**（`/product/{id}`），不是實際路徑。這很重要：若用實際路徑，每個 id 都會產生一條新序列，時間序列數量會爆掉（cardinality explosion）。

### 4.4 `histogram_quantile()`：讀延遲

**為什麼不看平均**：100 個請求裡 99 個 10ms、1 個 5 秒，平均 60ms 看起來很健康——但那個使用者等了 5 秒。**p99 = 「最慢的 1% 有多慢」才貼近使用者感受。**

```promql
histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
```

由內而外三層：

1. `rate(..._bucket[5m])` — `_bucket` 是一組 counter，每個 `le`（less-or-equal）標籤代表「耗時 ≤ le 秒的請求數」。同樣要先 `rate()`。
2. `sum by (le)` — 把各 uri/method 的同 `le` 加總。**只能保留 `le`**，其他標籤必須加總掉，否則算不出來。想看單一端點就寫 `sum by (le, uri)` 並在外層 `by (uri)`。
3. `histogram_quantile(0.99, ...)` — 從 bucket 分布**內插估算**分位數。它是估計值，精度取決於 bucket 邊界的疏密。

### 4.5 單位：一律用秒

指標名裡的 `_seconds` 不是裝飾，是 Prometheus 生態的慣例。`application-dev.yml` 明確設了 `base-time-unit: seconds`；Micrometer OTLP 的**預設是 milliseconds**，不設的話指標名會變成 `..._milliseconds_...`、值是毫秒，導致所有教材上的 PromQL 都對不上、Grafana 的 `s` 單位也會顯示錯（把 200ms 畫成 200 秒）。

---

## 5. 四個黃金訊號 → panel 對照

「黃金訊號」是 Google SRE 提出的最小監控集：**只有四個指標值得先看**。

| 訊號 | 問題 | 對應 panel |
|---|---|---|
| **Latency（延遲）** | 慢嗎？ | 回應延遲 p50 / p95 / p99 |
| **Traffic（流量）** | 多忙？ | 請求速率（依 uri） |
| **Errors（錯誤）** | 壞多少？ | 錯誤率（分「含 4xx」與「只看 5xx」兩條） |
| **Saturation（飽和度）** | 還剩多少餘裕？ | Bulkhead 剩餘併發額度、斷路器狀態、Hikari 等待連線數、JVM Heap |

飽和度值得多說一句：它是**唯一有預警能力**的訊號。連線池排隊、bulkhead 額度見底，通常發生在延遲/錯誤變差**之前**。而本專案的 bulkhead 設 `max-wait-duration: 0ms`（fail-fast 不排隊），滿載時延遲甚至不會變差——請求被很快拒絕掉了。所以只看延遲會漏掉這種情況。

---

## 6. 圖是空的：排錯決策樹

依序往下走，每一步都在「縮小範圍」而不是猜。

```
Grafana panel 空白
│
├─1. 有產生流量嗎？時間範圍對嗎？
│     指標要有請求才會動；右上角時間範圍預設 now-30m，若你十分鐘前打的請求
│     卻把範圍設成 now-5m 就會看不到。推送間隔 10s，打完請求要等一下。
│
├─2. Prometheus 自己查得到嗎？→ http://localhost:9090
│   │
│   ├─ 查得到 → 問題在 Grafana 這一端（hop 4）
│   │    · 指標名對不上？用 §3 步驟 3 列出真實名稱，比對 dashboard JSON 的 expr
│   │    · datasource 沒接上？Connections → Data sources → Prometheus → Save & test
│   │    · uid 不符？grafana/provisioning/datasources/prometheus.yml 的 uid 必須是
│   │      prometheus，dashboard JSON 引用的也是這個字串
│   │
│   └─ 查不到 → 資料沒進 Prometheus，往下查
│
├─3. app 有推出去嗎？（hop 1）
│     podman compose logs app | Select-String -Pattern 'otlp|4318|metrics'
│     · 連線失敗 → ALLOY_HOST 沒設對。容器內必須是 alloy（compose 已注入）；
│       本機 bootRun 會退回 localhost，此時要嘛把 alloy 的 4318 對外開著，
│       要嘛用 OTLP_METRICS_ENABLED=false 關掉推送以免每 10 秒噴一次錯
│     · 完全沒訊息 → 確認跑的是 dev profile（只有 dev 開 export.enabled，
│       base 的 application.yml 預設是 false，讓測試與 openapi profile 不推送）
│     · profile 對、設定也對，但 log 乾淨到沒有任何 OTLP 字樣 → 看 §6.1，
│       很可能是 OtlpMeterRegistry bean 根本沒被建出來（依賴不齊，靜默失敗）
│
├─4. Alloy 有轉出去嗎？（hop 2、3）
│     podman compose logs alloy
│     · 確認 config.alloy 的 prometheus.remote_write 指向 http://prometheus:9090/...
│     · 確認 prometheus 容器的 command 有 --web.enable-remote-write-receiver
│
└─5. 只有 p99 那個 panel 是空的，其他都正常？
      典型症狀 = 有 count/sum 但沒有 _bucket 序列。
      檢查 application-dev.yml 的
      management.metrics.distribution.percentiles-histogram 是否為
      http.server.requests 開啟。沒開的話 histogram_quantile() 一定回空值。
```

**最常見的三個原因**（都不在程式碼裡）：`percentiles-histogram` 沒開（p99 永遠空）、`base-time-unit` 沒設成 seconds（指標名帶 `_milliseconds`、查名字全部對不上）、以及下面這個 —— **少一個依賴，整條鏈路靜默不推送**。

### 6.1 最惡毒的一種：什麼都沒推，而且沒有任何錯誤訊息

這個坑真實發生過，而且**斷了 54 天沒人發現**。症狀是所有 panel 全空、Prometheus 一筆資料都沒有，但：

- app 啟動 log **乾淨無誤**，沒有任何 OTLP 相關警告
- `/actuator/health` 回 200、`/actuator/metrics` 也回 200 並列出一堆指標名

第二點是它難查的原因：`/actuator/metrics` 讀的是 composite registry，即使 OTLP registry 完全不存在，`SimpleMeterRegistry` 仍會在記憶體裡累積指標並照樣回應。**「端點查得到指標」不等於「指標推得出去」。**

根因在 Spring Boot 4 把 actuator 拆成多個模組之後。`OtlpMetricsExportAutoConfiguration` 的 `@ConditionalOnClass` 同時要求兩個類別：

| 類別 | 來自哪個 artifact |
|---|---|
| `io.micrometer.registry.otlp.OtlpMeterRegistry` | `io.micrometer:micrometer-registry-otlp` |
| `org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetryProperties` | `org.springframework.boot:spring-boot-opentelemetry` |

classpath 上只有前者時，條件不成立 → 沒有 `OtlpMeterRegistry` bean → 連負責推送的執行緒都不會被建出來 → **不推送也不報錯**。Spring Boot 不會為「條件沒過的 auto-configuration」印警告，這是設計如此（不然每次啟動會噴上百行）。

**診斷階梯**（每一步都在縮小範圍，而且都不需要改設定或重啟）：

```powershell
# 1. Prometheus 的 TSDB 裡最新一筆樣本是什麼時候？
#    maxTime 若停在很久以前，就不是「查詢寫錯」而是「根本沒新資料」。
(Invoke-RestMethod 'http://localhost:9090/api/v1/status/tsdb').data.headStats

# 陷阱：不要用 /api/v1/label/__name__/values 判斷。那是索引，
#       舊指標名會一直留著，看起來像有資料。要證明「活著」得用即時查詢：
(Invoke-RestMethod 'http://localhost:9090/api/v1/query?query=count({__name__=~".%2B"})').data.result
```

```bash
# 2. Alloy 到底有沒有收到東西？看 remote_write 的 WAL 有沒有長大。
#    （Alloy 映像沒有 curl/wget，它自己的 :12345 也只綁 127.0.0.1，所以看檔案。）
podman exec grafana-alloy sh -c 'ls -la /var/lib/alloy/data/prometheus.remote_write.local/wal/'
# segment 全是 0 bytes = Alloy 沒收到任何樣本 → 問題在 app 端（hop 1）

# 3. app 裡有沒有 OTLP 的推送執行緒？沒有執行緒就等於沒有 registry bean。
podman exec spring-boot-app sh -c 'for f in /proc/1/task/*/comm; do cat $f; done' | sort -u

# 4. 確認 @ConditionalOnClass 到底要求什麼（答案在 jar 裡，不必翻網路文件）
podman exec spring-boot-app sh -c 'unzip -p app.jar META-INF/spring-autoconfigure-metadata.properties' | grep -i otlp
```

修法就是把缺的模組補進 `build.gradle`（已修，見該檔可觀測性段落的註解）：

```gradle
implementation 'org.springframework.boot:spring-boot-opentelemetry'
```

**可帶走的教訓**：條件式 auto-configuration 沒生效是**靜默**的。凡是「該有資料卻完全沒有、又沒有錯誤訊息」的可觀測性問題，先懷疑依賴不齊、再懷疑設定寫錯 —— 順序不要顛倒，因為設定寫錯通常會有錯誤訊息，依賴不齊不會。

---

## 7. 佈建檔是怎麼運作的（`grafana/` 底下那三個檔案）

Grafana 的 datasource 與 dashboard **不在 UI 上手動建**，一律用佈建檔（provisioning）進 git。理由很實際：手動建的東西只存在 `grafana-data` 這個具名 volume 裡的 `grafana.db`，volume 一砍就全沒了，而且無法 code review、無法跟著 branch 走。

### 7.1 三個檔案各自的角色

| 檔案 | 是什麼 | 誰讀它 |
|---|---|---|
| `grafana/provisioning/datasources/prometheus.yml` | 「有一個叫 Prometheus 的資料來源，位置在 `http://prometheus:9090`」 | Grafana **啟動時**讀一次 |
| `grafana/provisioning/dashboards/default.yml` | **不是** dashboard，是「去哪個目錄找 dashboard」的 provider 設定 | Grafana 啟動時讀，之後按 `updateIntervalSeconds` 重掃 |
| `grafana/dashboards/app-overview.json` | dashboard 本體 | 由上面那個 provider 掃進來 |

最容易搞混的是中間那個：`provisioning/dashboards/` 底下放的是**指路的設定**，dashboard 的 JSON 在別的地方。

### 7.2 兩個掛載路徑的性質**完全不同**

```yaml
- ./grafana/provisioning:/etc/grafana/provisioning:ro   # 映像的預設路徑
- ./grafana/dashboards:/var/lib/grafana/dashboards:ro   # 我們自己挑的路徑
```

**`/etc/grafana/provisioning` 是被規定的。** 官方映像用環境變數把它設成絕對路徑（`grafana` 容器內實測）：

```
GF_PATHS_PROVISIONING=/etc/grafana/provisioning
GF_PATHS_DATA=/var/lib/grafana
GF_PATHS_CONFIG=/etc/grafana/grafana.ini
```

有趣的是 Grafana 自己 `defaults.ini` 裡的原生預設是**相對路徑** `conf/provisioning` —— 絕對路徑是**映像**的慣例，不是 Grafana 的常數。所以理論上你可以設 `GF_PATHS_PROVISIONING=/somewhere/else` 再掛到那裡。

**但目錄裡面的子目錄名是硬限制。** 映像內建的骨架就是完整清單：

```
/usr/share/grafana/conf/provisioning/
├── access-control/
├── alerting/
├── dashboards/      ← provider 設定（不是 dashboard 本體）
├── datasources/
└── plugins/
```

Grafana 對每個子目錄跑**不同的** provisioner，這些名字寫在程式碼裡。你不能自己開一個 `my-stuff/` 期待它被讀到；打成 `datasource/`（少一個 s）也只會被跳過。

> 這跟 §6.1 的 OTLP 依賴是**同一類失敗**：不是「設定錯 → 報錯」，而是「沒被掃到 → 什麼都沒發生」。基礎設施的設定檔很多都是這個模式，所以驗證一律要確認「東西真的出現了嗎」，而不是「有沒有報錯」。

檔案內容的 key 也是固定的：datasource 檔頂層必須是 `datasources:`、provider 檔必須是 `providers:`，兩者都要 `apiVersion: 1`。

**`/var/lib/grafana/dashboards` 則完全不是預設，是我們挑的。** `/var/lib/grafana` 是 `GF_PATHS_DATA`（`grafana.db` 住的地方），底下那層 `dashboards/` 沒有任何官方地位，唯一決定它的就是 `default.yml` 裡這行：

```yaml
options:
  path: /var/lib/grafana/dashboards    # 改成 /anything/you/like 都會動（compose 掛載跟著改）
```

從容器裡也看得出這層是外來的 —— 只有它的 owner 是主機 uid：

```
drwxrwxrwx  1 1000    1000  dashboards      ← 掛載進來的
-rw-r-----  1 grafana root  grafana.db      ← 其餘都屬於 grafana 使用者
```

**為什麼不乾脆把 JSON 塞進 `/etc/grafana/provisioning/dashboards/` 跟 `default.yml` 放一起？** 技術上可以（把 `options.path` 指到同一層即可，很多範例這麼做）。這裡刻意分開，因為那個目錄的語意是「給 Grafana 掃 provider 設定」，把 dashboard JSON 混進去會讓同一個目錄躺著兩種語意不同的檔案。

還有一個看起來像限制、其實不是的東西：`defaults.ini` 有 `permitted_provisioning_paths = devenv/dev-dashboards|conf/provisioning`。那個管的是新版 Git Sync / local repository 功能，**不管** dashboard file provider 的 `options.path` —— 實證就是我們的 `/var/lib/grafana/dashboards` 不在清單裡卻正常載入。

**巢狀掛載**：`grafana-data:/var/lib/grafana` 與 `./grafana/dashboards:/var/lib/grafana/dashboards:ro` 是一個掛在另一個裡面。這在 podman/docker 都合法，runtime 按路徑深度排序，較深的蓋在較淺的上面。所以 `grafana.db`（UI 上的改動、註解、偏好設定）仍留在具名 volume，只有 `dashboards/` 那一層被 repo 的唯讀內容覆蓋。**compose 裡兩行的先後順序不影響結果。**

### 7.3 這三個檔案是手寫的，但性質分兩類

跟 `config.alloy` 的類比只有一半成立：

| | `config.alloy` | `provisioning/*.yml` | `app-overview.json` |
|---|---|---|---|
| 格式性質 | Alloy 的 DSL（HCL 風格），是**宣告元件並接線**的小程式 | Grafana provisioning schema，攤平的資料 | Grafana **dashboard model** —— 內部資料結構的序列化 |
| 誰定義格式 | Alloy 文件 | Grafana 文件 | Grafana 前後端 schema，**隨版本演進**（故有 `schemaVersion`） |
| 正常怎麼產出 | 手寫 | 手寫 | **通常在 UI 拉好再匯出** |

兩個 yml 確實「像 `config.alloy`」：啟動時讀的設定檔、手寫、格式由該工具定義、改了要重啟。唯一差別是 `config.alloy` 在描述一條 pipeline（receiver → exporter → remote_write 互相 `forward_to`），provisioning yml 沒有接線概念。

**`app-overview.json` 不一樣，它是 UI 的產物格式。** 標準流程是在 Grafana 上拖拉建好 → Dashboard settings → JSON Model → 複製出來存檔。這份是直接手寫的，理由：UI 匯出的 JSON 帶大量雜訊（`__inputs`、`__requires`、`pluginVersion`、所有預設值展開、亂數 `id`），手寫才能只留必要的 key 讓檔案可讀；更重要的是每個 panel 的 `description` 要當教材寫，那是 UI 匯出不會幫你填的欄位。

手寫的兩個注意事項：

- 這是**最小子集**，權威 schema 是 UI 產的那份。檔案宣告 `schemaVersion: 39`，若比 Grafana 自身版本舊，讀進來會自動 migrate。
- 頂層**故意沒有 `id`** —— 佈建的 dashboard 帶 `id` 會跟 `grafana.db` 裡既有的撞號。穩定識別靠 `uid: "app-overview"`。

`prometheus.yml` 的 `uid: prometheus` **寫死**是同一個思路：讓 dashboard JSON 有固定字串可引用。若讓 Grafana 隨機產 uid，佈建的 dashboard 會找不到資料來源 —— panel 全空，而錯誤訊息不明顯。

### 7.4 改動怎麼生效，以及 UI 上的改動去了哪裡

| 改什麼 | 怎麼生效 |
|---|---|
| `grafana/dashboards/*.json` | provider 每 10 秒重掃，**重新整理瀏覽器**即可 |
| `grafana/provisioning/**` | 需 `podman compose restart grafana`（啟動時才讀） |

`default.yml` 設了 `allowUiUpdates: true`，所以你**可以放心在 UI 上直接改** —— 改動只寫進 `grafana.db`，**不會**寫回你的 JSON 檔（那是唯讀掛載）。這正是推薦的工作流：在 UI 上拉到滿意 → Dashboard settings → JSON Model → 複製回 `grafana/dashboards/app-overview.json` → commit。

---

## 8. 自己加一個 panel

改動要進 git，所以**不要只在 UI 上按存檔**——理由與機制見 §7.4。

1. 先在 **Prometheus UI**（<http://localhost:9090>）把 PromQL 調到出正確結果為止。這一步不要在 Grafana 做，Grafana 多一層變數與時間範圍的干擾。
2. 或在 Grafana 直接 Edit panel 試版面，滿意後開 **Dashboard settings → JSON Model**，把 JSON 複製出來。
3. 把 panel 物件貼進 `grafana/dashboards/app-overview.json` 的 `panels` 陣列，注意：
   - `id` 不能與現有 panel 重複
   - `gridPos` 的 `y` 要接在下方（`w` 最大 24）
   - `datasource` 寫 `{"type": "prometheus", "uid": "prometheus"}`
   - **`description` 請寫「這句 PromQL 為什麼這樣寫」**——這份 dashboard 是要當教材讀的
4. 存檔後重新整理瀏覽器即可（**不必重啟容器**，機制見 §7.4）。

值得自己動手加的幾個：`rate(hikaricp_connections_timeout_total[5m])`（連線等到超時的次數）、`resilience4j_ratelimiter_available_permissions`（限流剩餘額度）、`sum by (uri, status) (rate(http_server_requests_seconds_count[5m]))`（哪個端點在回什麼狀態碼）。

---

## 9. 這批刻意沒做的（延後項目）

| 項目 | 為什麼延後 | 補上之後能做到什麼 |
|---|---|---|
| **Tracing**（Micrometer Tracing + Tempo） | 需新增依賴與 compose 服務，範圍比「看得見」大 | 從 p99 尖峰**直接跳到那一筆**慢請求，看它在 `*Client` 跨模組呼叫的哪一段卡住。這才是原本 `LoggingAspect` 想做卻做不到的方法級追蹤的正解 |
| **`@Timed` 業務層指標** | `http.server.requests` 與 `resilience4j.*` 已覆蓋大部分需求；等真的有「想知道扣庫存那段花多久」的需求再加 | 用業務語彙命名的指標（如 `order.create`）。需先開 `management.observations.annotations.enabled`（Spring Boot 預設 `false`） |
| **結構化日誌 + Loki** | 要先把 `accountId`/`orderId` 放進 MDC 而非訊息字串 | 用欄位查日誌，並與 trace id 關聯 |
| **告警規則** | 有 dashboard 才知道正常長什麼樣，才定得出閾值 | 錯誤率 > 5% 持續 5 分鐘就通知，不必盯著螢幕 |

---

## 相關文件

- [`docs/agents/09-monitoring.md`](./agents/09-monitoring.md) — 鏈路與端點的架構描述、HEALTHCHECK 運作機制
- [`docs/resilience4j-configuration-guide.md`](./resilience4j-configuration-guide.md) — 飽和度 panel 背後的 bulkhead / 斷路器 / 限流設定
- `config.alloy`、`prometheus.yml`、`docker-compose.yml` — 管線本體
- `grafana/` — datasource 與 dashboard 佈建檔
