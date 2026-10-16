# 指標改由 Alloy scrape（第三條路）

指標鏈從「app 以 OTLP push 給 Alloy」改成「Alloy 去 scrape app 的 `/actuator/prometheus`，再 remote_write 給 Prometheus」。追蹤鏈不動。

## 動機

`docs/monitoring-usage-guide.md` §2.1 記錄了全程 push 的代價，其中最大的一項是**失去 `up`**。app 掛了、網路斷了、Alloy 掛了、依賴不齊而靜默不推送，這幾種情況在查詢上全都長成「沒資料」。`docs/monitoring-needs-first.md` §6.1 因此只能用 `time() - timestamp(...)` 搭配 `absent(...)` 兩句互補的查詢來補救。

§2.1 同時也指出了第三條路：collector 對 app 用 pull，對後端用 push。這樣可以：

- 拿回 Prometheus 每次 scrape 自動產生的 `up{job, instance}`，「資料不見了」縮成一句 `up == 0`
- 拿回 `curl /actuator/prometheus` 這種除錯方式：直接看到 app 自認的指標，不必沿鏈路一段段猜
- 保留 Alloy 當集中處理點，以及 remote_write 的網路方向
- 指標走 pull、追蹤走 OTLP push 的混搭，本身就是實務上常見的形態，也值得學

原本選 push 的理由（簡單、OTLP 教學價值）大部分仍然由追蹤鏈保有，所以這次轉換放棄的不多。

## 用語釐清

Micrometer 是埋點用的 facade（角色類似 SLF4J），`http.server.requests` 這類指標是它在產生。`micrometer-registry-otlp` 和 `micrometer-registry-prometheus` 是它的兩個**輸出 backend**：一個以 OTLP 推出去，一個開出 `/actuator/prometheus` 給人來抓。這次是**換 registry**，Micrometer 本身與所有埋點都不變。

## 決策

### 1. Registry 只短期並存，最終只留 scrape

- 加入 `io.micrometer:micrometer-registry-prometheus`。
- `micrometer-registry-otlp` 由 `spring-boot-starter-opentelemetry` 帶入，追蹤鏈也需要這個 starter，所以**不移除依賴**，只關掉 dev profile 的 OTLP 指標匯出。
- 並存只用於過渡期的對照（比對名稱與數值），合併前一定要關掉 OTLP 指標匯出。理由：兩條線同時進 Prometheus，同一個指標會出現兩組 series（scrape 那組帶 `job`/`instance`，OTLP 那組沒有），`sum by (uri)` 會**靜默地算成兩倍**，不會報錯。

### 2. `/actuator/prometheus` 用 Basic 認證（`internal` 帳號），不放行

- health 會放行，是因為 Dockerfile `HEALTHCHECK` 的 `wget` 是下游 E2E 依賴的契約，不方便帶憑證。scraper 是我們自己寫的設定，Alloy 的 `basic_auth` 一段就能處理，所以放行 health 的理由不適用於這裡。
- 這個端點會暴露整份 API 的 uri 樣板、錯誤率、連線池與 bulkhead 狀態，而 8787 有發佈到宿主。`/actuator/metrics` 現在本身就需要認證，只放行 prometheus 會前後不一致。
- 不採用「management 改到另一個 port 再放行」：health 會跟著搬走，等於破壞 `HEALTHCHECK` 契約。
- 密碼用 `{noop}` 逐字比對（見 `docs/agents/06-architecture.md` 安全段落），每次 scrape 的認證成本可以忽略。
- 用 `internal` 而不是 `api`：scrape 是服務對服務的呼叫，與 `*Client` 自呼叫同一類。目前沒有任何授權規則區分這兩個角色，這裡只是語意上的選擇。

## 非目標

- 追蹤鏈（app → Alloy → Tempo）不動
- 不加告警規則：仍是 guide §10 的延後項目。不過這次做完之後，第一條規則可以直接寫成 `up == 0`
- 不加 service discovery：compose 內寫死一個 target 就好

## 完成後

新增 `docs/adr/0003-metrics-via-collector-scrape.md`，記錄這次推翻了 guide §2.1「全程 push」的決策，以及上面兩條決策的理由。

## Issues

- [01-switch-metrics-to-alloy-scrape](issues/01-switch-metrics-to-alloy-scrape.md)
