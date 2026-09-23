# 從需求反推監控：做減法的判準

> **目標**：拿到一套「先有 metric、才有 panel」的監控 boilerplate 之後，學會判斷哪些該留、哪些該換、哪些該刪，並知道留下的每一個為什麼值得留。

本文件是 [`monitoring-usage-guide.md`](./monitoring-usage-guide.md)（以下簡稱 **guide**）的前一步：guide 回答「這個功能怎麼用、壞了怎麼查」，本文件回答「**我到底需不需要它**」。凡是 guide 已經講過的內容，這裡只給節號，不重寫。

出處標記：`[本專案]` 是這個 repo 的實際狀況與結論；`[通用補充]` 是不綁本專案、可以帶走的觀念。

---

## 1. 為什麼 boilerplate 的順序是反的 `[通用補充]`

AI 產生（或照教學抄來）的監控，順序幾乎都是 **metric-first**：

```
Micrometer 剛好有這個 metric → 畫一個 panel → 以後應該用得到吧
```

正確的順序是 **question-first**：

```
我在某個時刻需要回答某個問題 → 需要什麼訊號才答得出來 → 畫成什麼樣最快讀懂
```

兩者的產物乍看一樣（都是一排圖），差別出在壓力下：metric-first 的 dashboard 在出事時會給你**一堆線**，question-first 的會給你**答案**。

對**自己一個人維護**的系統，還有一個推論：**每個 panel 都是負債** —— 要維護、要記得它的意思、要在半夜兩點還看得懂。所以 dashboard 應該小到**一個螢幕不用捲動**，而「再加一個 panel」幾乎永遠是錯的方向；該做的是「**換掉一個**」。

---

## 2. 需求規格：七個「有人在等答案」的時刻 `[通用補充]`

需求不是坐著想出來的，是從「**有人在等你回答**」的時刻來的。自己維護的後端，這種時刻就這七個：

| # | 時刻 | 你要在幾秒內回答 |
|---|---|---|
| Q1 | 有人說「系統壞了」 | 真的壞了嗎？壞多久了？影響多大？ |
| Q2 | 有人說「很慢」 | 真的慢嗎？**哪個** endpoint？從什麼時候開始？ |
| Q3 | 你要動手改東西**之前** | 現在的基準線是多少？ |
| Q4 | 你改完**之後** | 有沒有變差？ |
| Q5 | 你要決定花錢或調參數 | 瓶頸在哪？加機器、調 pool，還是改程式？ |
| Q6 | 有人問「這一筆請求發生什麼事」 | 完整路徑 ← **trace 存在的唯一理由** |
| Q7 | 你想去睡覺 | 什麼情況該把我叫醒？ |

**判準**：每個 panel 都必須能指到其中至少一問。指不到的就是裝飾品 —— 不管它看起來多專業。

> guide §5 用的是 Google SRE 的「四個黃金訊號」（延遲、流量、錯誤、飽和）。兩者不衝突：黃金訊號是「**該量什麼**」，七問是「**什麼時候要用、要答出什麼**」。一個 panel 屬於哪個黃金訊號，不代表它答得出任何一問 —— §4 的 JVM Heap 就是例子。

---

## 3. 四層漏斗：每一層回答不同的問題 `[通用補充]` + `[本專案]`

| 層 | 回答什麼 | 怎麼用 |
|---|---|---|
| **告警** | 有事嗎？ | **主動來找你**（Q7） |
| **Dashboard** | 哪裡有事？ | 被叫醒之後才打開（Q1–Q5） |
| **Trace** | 這一筆請求發生什麼事？ | 縮到單一請求（Q6） |
| **Log** | 為什麼？ | 最後一哩 |

這是一個逐層收窄的漏斗。每往下一層，範圍就縮小一級，細節多一級。

`[本專案]` 現況：**只有中間兩層**。告警刻意沒做（guide §10），log 也不在 Grafana 裡（沒接 Loki，log 只在 app 的 console）。

拿「下單時庫存不足」走一遍漏斗：

1. **告警** —— 沒有，也不該有：這是 `400 PRODUCT_STOCK_NOT_ENOUGH`，是業務正常運作的一部分。
2. **Dashboard** —— 錯誤率面板**上面那條線**（含 4xx）會升，**下面那條**（只看 5xx）不動。只有下面那條該讓你緊張。
3. **Log** —— console 裡會有兩行 WARN（`/order` 與 loopback 的 `/product/reserve` 各一行），同一個 traceId、不同 spanId。
4. **Trace** —— 把那個 traceId 貼進 Explore 的 TraceID（guide §9.3），看到兩層 span 的樹。

注意第 3、4 步的順序跟表格是反的：本專案沒有 Loki，所以實務上是先在 console 找到 log，再拿 traceId 去查 trace。等哪天接了 Loki，順序才會回到漏斗的樣子。

---

## 4. 審查現有的 7 個 panel `[本專案]`

對象：`grafana/dashboards/app-overview.json`。

| Panel | 服務哪一問 | 判定 | 理由 |
|---|---|---|---|
| 錯誤率 —— `outcome="SERVER_ERROR"` 那條線 | Q1 | ⭐ **留，全場最重要** | 5xx 才是「系統自己出錯」。bulkhead 滿載（`BULKHEAD_FULL`）與斷路器打開（`CIRCUIT_OPEN`）都回 503，也會落在這條線 |
| 錯誤率 —— `outcome!="SUCCESS"` 那條線 | Q1（弱） | **留但降級** | 4xx 大多是正常業務（庫存不足、查無資料），會把真訊號淹掉。但有一個例外藏在裡面：**限流拒絕回 `429 RATE_LIMITED`**，它是飽和訊號，卻被歸在 4xx |
| Hikari 等待連線的執行緒數 | Q5 | ⭐ **留** | `pending > 0` ＝「有執行緒在排隊等 DB 連線」，是整個 dashboard 最接近**零誤報**的飽和訊號 |
| 請求速率（依 uri） | Q2、Q3、Q5 | **留** | 唯一帶 `uri` 維度的 panel，是「**哪個** endpoint」的唯一入口 |
| 回應延遲 p50 / p95 / p99 | Q2、Q3、Q4 | **留但半殘** | `sum by (le)` 把所有 uri 混在一起：偵測得到「變慢了」，卻答不出 Q2 的下一句「哪個 endpoint」 |
| Bulkhead 剩餘併發額度、斷路器狀態 | Q5 | **留但換位置** | 它們是**解釋層**（為什麼錯）而不是**偵測層**（有沒有錯）：真的滿載或跳脫時，503 已經先在 SERVER_ERROR 那條線上出現了 |
| JVM Heap 使用量 | 無 | 🔴 **首選換掉** | GC 造成的鋸齒波，單看推不出任何動作：「heap 用了 800MB」—— 然後呢？ |

**這張表就是你的作業清單。** 以下每一項都是一個小 commit，改完照 guide §7.4 的流程（UI 上調到滿意 → JSON Model → 複製回 `app-overview.json`）：

- [ ] **延遲面板**：選一條路
  - 改成依 endpoint 分線：`histogram_quantile(0.99, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))`，endpoint 多時再包一層 `topk(5, ...)`
  - 或保持總覽，但在 `description` 寫明「只回答『有沒有變慢』，要知道哪個 endpoint 請到 Explore 加 `uri`」
- [ ] **錯誤率上面那條線**：換成 `sum by (status) (rate(http_server_requests_seconds_count{outcome="CLIENT_ERROR"}[5m]))`，讓 429 自己成一條線，不再跟 400/404 混在一起
- [ ] **Bulkhead / 斷路器**：移到 dashboard 下半部，或收進一個預設折疊的 row（例如命名為「為什麼」）
- [ ] **JVM Heap**：換成 GC 停頓。GC 停頓時間直接等於使用者感受到的延遲，能連回 Q2。查詢見 §7 末尾

---

## 5. 最鋒利的那把刀：「下一個動作」測試 `[通用補充]` + `[本專案]`

§2 的七問用來判斷「**該不該有**」；這一節的測試用來判斷「**留下的有沒有用**」。對每個 panel 寫一句：

> **「如果這條線現在異常，我的下一個動作是什麼？」**

寫不出來，就是裝飾品。

- **JVM Heap**：「heap 衝到 90%」→ ……GC 本來就會讓它衝高再掉下來。寫不出動作。
- **Hikari pending**：「pending 持續 > 0」→ 去 Explore 看同一時段哪個 uri 的延遲在升，再到 Tempo 找那段時間最慢的 trace，看是慢查詢還是 pool 太小。**一句話就寫完了。**

差別非常明顯。

`[本專案]` 現在的慣例（guide §8）是：每個 panel 的 `description` 要解釋「這句 PromQL 為什麼這樣寫」。7 個 panel 都有照做。**建議升級成三件事**：

```
服務哪一問（Q1–Q7）＋ 異常時的下一個動作 ＋ PromQL 為什麼這樣寫
```

好處是 dashboard 本身就成為減法的紀錄：以後任何人（包括半年後的你或 AI）想加 panel，寫不出前兩句就不該加進來。

> 要採用這個慣例，就是**慣例變更**，得一併更新 guide §8 與 `docs/agents/09-monitoring.md` 的「Grafana 佈建」一節，否則下一次 AI 幫你加 panel 時還是會照舊規則寫。

---

## 6. 比「panel 選錯」嚴重得多的兩個缺口 `[本專案]`

### 6.1 分不出「沒有錯誤」和「沒有資料」

本專案是 OTLP **push**，不是 scrape（guide §2.1），所以 Prometheus 裡**沒有 `up` 這個 series**。後果是：app 掛掉時，各個 panel 會先緩緩下降、約 5 分鐘後變成一片空白 —— 看起來跟「一切正常、剛好沒流量」沒有兩樣。你不會看到任何紅色訊號。

偵測「資料不見了」要自己寫，而且兩種寫法各自只涵蓋一段時間：

```promql
# (a) 多久沒收到新資料（秒）
time() - max(timestamp(http_server_requests_seconds_count{application="demo"}))

# (b) 這個 metric 是否已經完全消失
absent(http_server_requests_seconds_count{application="demo"})
```

實測（停掉 app 容器，每隔一段時間各查一次）：

| 停掉後 | (a) | (b) |
|---|---|---|
| 60 秒 | `61` | 空集合 |
| 180 秒 | `181` | 空集合 |
| 290 秒 | `292` | 空集合 |
| 330 秒 | **空集合** | `1` |

原因是 Prometheus 的 staleness 預設回看 5 分鐘：超過 5 分鐘沒有新樣本，這個 series 就被當成不存在。所以：

- (a) 在前 5 分鐘能精確量出「斷了多久」，**過了 5 分鐘就消失**。若只拿它設「超過 60 秒就告警」，告警會在斷線滿 5 分鐘時**自動解除** —— 偏偏是事情最嚴重的時候。
- (b) 前 5 分鐘完全沒反應，5 分鐘之後才接手。

**兩句要合起來用**：`(a) > 60 or (b)`。這是「語法正確、答案靜默是錯的」（§7）的又一個例子，也說明了為什麼不能只看 AI 給的查詢「跑得出結果」就算數 —— 要拿真實的故障情境去試。

### 6.2 沒有告警

Dashboard 是給「**已經知道有事**」的人看的。自己一個人維護，沒有人在盯螢幕 —— 所以缺的不是更多 panel，而是一條規則。

**第一條規則不該是錯誤率，而是 6.1 的「資料不見了」**：錯誤率告警在 app 掛掉時反而是安靜的（沒有資料就沒有錯誤），只有「資料不見了」這條會在最糟的情況下叫醒你。

告警仍是 guide §10 的延後項目，本文件只說明方向與理由，不實作。

---

## 7. PromQL：語法交給 AI，你只守住四種靜默錯誤 `[通用補充]` + `[本專案]`

有 AI 之後，PromQL 的語法（`rate` 跟 `irate` 差在哪、`by` 跟 `without`、subquery）確實不必背，AI 一次就寫對。你要練的是**把需求講清楚**，並且**看得懂、改得動** AI 給的查詢。

但要補一個關鍵：真正危險的不是**寫錯** —— 寫錯會報錯，你會發現 —— 而是**語法完全正確、答案靜默是錯的**。AI 無從知道你的 metric 實際長什麼樣，所以擋不住這類錯誤。這類錯誤只有四種：

| 陷阱 | 症狀 | 延伸 |
|---|---|---|
| counter / gauge 搞錯 | 對 gauge 包 `rate()`，得到毫無意義的數字，但圖照樣畫得出來 | guide §4.1 |
| histogram bucket 太粗 | `histogram_quantile` 回一個看起來很精確的值，其實只是 bucket 邊界內插出來的 | guide §4.4 |
| 聚合掉了需要的維度 | 「p99 = 800ms」不屬於任何一個 endpoint | §4 的延遲面板 |
| rate 時間窗太短 | 窗小於推送間隔的 2 倍時，圖上出現斷斷續續的空洞 | 本專案 `step: 10s`，窗至少 `[30s]`；現有面板用 `[5m]`，安全 |

`[本專案]` 第二個陷阱在這裡有個更隱蔽的版本：**只有 `http.server.requests` 開了 histogram bucket**（`application-dev.yml` 的 `percentiles-histogram`，約 70 個 `le` 邊界）。可是在 Prometheus 裡，其他 timer（`jvm_gc_pause_seconds`、`hikaricp_connections_acquire_seconds`、`spring_data_repository_invocations_seconds`……）**也都查得到 `_bucket`** —— 只是每個都只有一個 `le="+Inf"`。對它們寫 `histogram_quantile` 不會報錯，而是回傳 `NaN`，圖上一片空白，很容易誤以為是「沒流量」。

判斷方式：先查 `count by (le) (<metric>_bucket)`。只回一行 `+Inf` ＝ 這個 metric 沒有真正的分布，別對它算分位數。

### 描述給 AI 的範本

```
我有一個 Prometheus metric：<名稱>
- 型別：<counter / gauge / histogram>
- labels：<列出來，例如 uri, method, status, outcome>
- 推送間隔：10 秒
我要回答的問題是：<七問中的哪一個，用自己的話講>
請給我 PromQL。然後另外告訴我：這個查詢在什麼情況下會「不報錯但算錯」？
```

檢查 AI 產出時，**不要改它、先問它**：「這句在什麼情況下會回傳錯的結果？」這一問練的是「懷疑自己的查詢」，而那正是 §5 的 `description` 該寫的東西。

### 範例：§4 那個 JVM Heap 換成 GC 停頓

GC 停頓正是上面那種「只有 `+Inf`」的 timer，所以不能用 `histogram_quantile`，改看平均與最大值：

```promql
# 每次 GC 平均停多久（秒）
sum(rate(jvm_gc_pause_seconds_sum[5m])) / sum(rate(jvm_gc_pause_seconds_count[5m]))

# 最近一次推送週期內最長的一次停頓（秒）
max(jvm_gc_pause_max_seconds)
```

注意第二句的名稱是 `jvm_gc_pause_max_seconds`，**不是**直覺會猜的 `jvm_gc_pause_seconds_max` —— 經 Alloy 轉成 Prometheus 命名時，單位後綴被移到最後。猜錯名稱時 Prometheus 不報錯，只回空結果；AI 給的名稱也一樣要先在 Prometheus 的 metric 瀏覽器確認過。這個值每個推送週期（10 秒）重新計算，那段時間沒發生 GC 就是 `0`。

「下一個動作」測試：停頓持續超過 100ms → 對照同時段的延遲面板，確認使用者有感；有感才去調 heap 或 GC。

---

## 附錄 A：Grafana UI 只學到剛好夠用 `[通用補充]`

答對「我需要什麼」之後，UI 要學的範圍會縮得很小：

- **先把左側選單分三堆**：現在就有東西的／接上別的東西才有用的（Alerting、Logs）／這個 stack 用不到的。以你畫面上的實際選單為準 —— Grafana 用 `:latest`，選單名稱會隨版本改。
- **95% 的時間在三個地方**：Dashboards、Explore、Connections（Data sources）。
- **資源優先順序**：
  1. <https://play.grafana.org> —— Grafana 官方的公開 demo，幾百個真實 dashboard，每個都能開 Dashboard settings → JSON Model 看它怎麼寫
  2. 官方文件當手冊查，不要從頭讀（最常查的是 Transformations 和 Variables）
  3. YouTube 只看單一功能的短片
  4. Udemy 不建議當主線：大半篇幅在教安裝，而且 UI 改版快、截圖容易過期
- UI 上的改動只存進 `grafana.db`，不會寫回 repo 裡的 JSON（guide §7.4）。放心亂改，改壞了重啟容器就回來了。
- 任何頁面按 `?` 會列出鍵盤快捷鍵。

---

## 附錄 B：接下來去哪

| 想做什麼 | 去哪 |
|---|---|
| 五分鐘把 stack 跑起來、造流量 | guide §3 |
| 看懂 dashboard 裡的 PromQL | guide §4 |
| 圖是空的 | guide §6、§6.1 |
| 改了佈建檔怎麼生效 | guide §7.4 |
| 加／換一個 panel 的完整步驟 | guide §8 |
| 從 log 或指標跳到 trace | guide §9.3 |
| 刻意延後的項目（告警、Loki） | guide §10 |
| 為什麼是 push、監控的三層切分 | [`docs/agents/09-monitoring.md`](./agents/09-monitoring.md) |
