# Resilience4j 負載測試筆記

本文件記錄 Resilience4j 各元件（RateLimiter、Bulkhead、CircuitBreaker）在負載測試中的行為觀察與驗證結果。

---

## RateLimiter

### 測試場景

- **API**: `GET /product`（查詢所有商品列表，100 筆資料，無分頁）
- **工具**: JMeter
- **配置**:
  - `product-read`: `limit-for-period: 1000`, `limit-refresh-period: 1s`, `timeout-duration: 0ms`
  - Bulkhead `product-read`: `max-concurrent-calls: 1000`
  - HikariCP: `maximum-pool-size: 500`

### JMeter 設定

| 參數 | 值 |
|------|-----|
| Number of Threads | 1500 |
| Ramp-up Period | 0 |
| Loop Count | 1 |

### 測試結果

| 指標 | 值 |
|------|-----|
| Throughput | 260 req/s |
| Average Response Time | 2818 ms |
| Error Rate | 0.00%（無 HTTP 429） |
| 總執行時間 | ~5.77 秒 |

### 觀察：RateLimiter 沒有觸發

#### 驗證 RateLimiter 確實有在運作

將 `limit-for-period` 從 1000 改為 10 後，用 50 個請求測試，確認有大量請求收到 HTTP 429。
→ **RateLimiter 的 AOP 攔截正常運作，配置正確。**

#### 為什麼原本配置 (1000/s) 沒有觸發？

**根本原因：JMeter 的實際請求到達速率未超過 RateLimiter 的閾值。**

即使 JMeter 設定 Ramp-up=0，1500 個請求並不會在同一毫秒內全部到達 Service 層的 RateLimiter：

1. **JMeter 線程啟動時間**：JVM 創建 1500 個 Java 線程並建立 TCP 連接需要時間（估計 1-2 秒）
2. **TCP 連接建立**：1500 個 HTTP 連接的三次握手造成時間分散
3. **跨越多個 refresh cycle**：如果請求分散在 2 秒以上到達，RateLimiter 有 2000+ 個 permit 可用（每秒刷新 1000），1500 < 2000，全部通過

**關鍵理解：** Resilience4j RateLimiter 的 permit 是在**方法進入時**消耗的（不是完成時）。`limit-refresh-period: 1s` 表示每秒補充 `limit-for-period` 個 permit。如果請求到達時間跨越了多個 1 秒的 cycle，可用的 permit 總量會累積。

#### Throughput 只有 260 req/s 的原因

系統的實際瓶頸不在 RateLimiter，而在更下層：

```
請求到達（受限於 JMeter + 網路，分散在多個 cycle）
    ↓
RateLimiter: 1000/s        ← 未觸發（到達速率 < 閾值）
    ↓
Bulkhead: 1000 concurrent  ← 未觸發（DB 連接池先成為瓶頸）
    ↓
HikariCP: 500 connections  ← 實際瓶頸
    ↓
DB 查詢（100 筆商品全量查詢 + show-sql: true 的 I/O 開銷）
    ↓
實際 throughput: 260 req/s
```

### 結論

| 問題 | 答案 |
|------|------|
| RateLimiter 配置是否正確？ | ✅ 是 |
| RateLimiter AOP 是否生效？ | ✅ 是（改小閾值可驗證） |
| 原本測試為何沒觸發？ | JMeter 實際到達速率未超過 1000/s |
| 配置是否有意義？ | ✅ 是，在真實生產環境中會發揮作用 |

### 什麼情況下 RateLimiter 會真正觸發？

1. **真實生產環境**：大量客戶端同時發送請求，到達速率超過閾值
2. **輕量 API**：不查 DB 的 endpoint，單機 throughput 可達數萬 req/s
3. **分散式負載測試**：多台 JMeter 或使用 Gatling/k6 等更高效工具
4. **降低閾值測試**：將 `limit-for-period` 改小（如 10）可快速驗證

### 驗證 RateLimiter 狀態的方法

以 `product-read` 為例，查看該 RateLimiter 的可用 permit 數量：

#### Actuator Endpoint

```bash
# 查看 product-read 的可用 permit（正常閒置時應為 1000）
GET http://localhost:8787/actuator/metrics/resilience4j.ratelimiter.available.permissions?tag=name:product-read

# 回應範例：
# {
#   "name": "resilience4j.ratelimiter.available.permissions",
#   "measurements": [{"statistic": "VALUE", "value": 1000.0}],
#   "availableTags": [{"tag": "application", "values": ["demo"]}]
# }
```

壓測期間如果值明顯低於 1000（甚至為負數），表示 RateLimiter 正在消耗 permit。

#### Grafana（透過 Prometheus）

本專案透過 Actuator 暴露 Prometheus 格式的 metrics（`/actuator/prometheus`），可在 Grafana 中使用以下 PromQL 查詢：

```promql
# 查看 product-read 的可用 permit 變化趨勢
resilience4j_ratelimiter_available_permissions{name="product-read", application="demo"}

# 查看所有 RateLimiter 的可用 permit（按 name 分組）
resilience4j_ratelimiter_available_permissions{application="demo"}
```

**Grafana Dashboard 建議面板：**

| Panel 名稱 | PromQL | 說明 |
|------------|--------|------|
| RateLimiter Available Permits | `resilience4j_ratelimiter_available_permissions{name="product-read"}` | 可用 permit 即時值，低於 0 表示有請求被拒絕 |
| RateLimiter Waiting Threads | `resilience4j_ratelimiter_waiting_threads{name="product-read"}` | 等待中的線程數（timeout-duration > 0 時才有意義） |
| RateLimiter Successful Calls | `rate(resilience4j_ratelimiter_calls_total{name="product-read", kind="successful"}[1m])` | 每秒成功通過的請求數 |
| RateLimiter Rejected Calls | `rate(resilience4j_ratelimiter_calls_total{name="product-read", kind="failed"}[1m])` | 每秒被拒絕的請求數 |

---

## Bulkhead

### 配置

```yaml
product-read:
  max-concurrent-calls: 1000
  max-wait-duration: 0ms    # fail-fast
```

### 行為說明

- Bulkhead 限制的是**同時執行中的請求數量**（並發數）
- 當並發數達到 `max-concurrent-calls` 時，新請求會收到 `BulkheadFullException`（HTTP 503）
- `max-wait-duration: 0ms` 表示不等待，立即拒絕

### 與 RateLimiter 的差異

| 特性 | RateLimiter | Bulkhead |
|------|-------------|----------|
| 限制維度 | 時間窗口內的請求總數 | 同時執行中的請求數 |
| 刷新機制 | 每個 period 補充 permit | 請求完成後釋放 slot |
| 適用場景 | 防止突發流量 | 防止資源耗盡 |
| HTTP 錯誤碼 | 429 Too Many Requests | 503 Service Unavailable |

### 執行順序

Resilience4j 預設的裝飾器執行順序（外到內）：

```
Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead → 實際方法
```

即：請求先經過 RateLimiter，通過後再經過 Bulkhead。

### 負載測試觀察

在 `GET /product` 的測試中，Bulkhead (1000 concurrent) 未觸發，原因是 HikariCP (500 connections) 先成為瓶頸，使得實際並發數受限於 DB 連接池大小。

### 驗證方法

```bash
GET http://localhost:8787/actuator/metrics/resilience4j.bulkhead.available.concurrent.calls?tag=name:product-read
```

---

## CircuitBreaker

### 配置

```yaml
ProductService:
  base-config: default
  # default 配置：
  #   sliding-window-type: COUNT_BASED
  #   sliding-window-size: 100
  #   minimum-number-of-calls: 50
  #   failure-rate-threshold: 50%
  #   wait-duration-in-open-state: 60s
  #   permitted-number-of-calls-in-half-open-state: 10
```

### 行為說明

- CircuitBreaker 放在**類級別** (`@CircuitBreaker(name = "ProductService")`)
- 當失敗率超過 50%（在最近 100 個請求中），電路斷開
- 斷開後等待 60 秒，進入半開狀態，允許 10 個請求嘗試
- 如果嘗試成功，電路關閉；如果仍然失敗，繼續斷開

### 與 RateLimiter/Bulkhead 的協作

```
@CircuitBreaker(name = "ProductService")     ← 類級別，保護整個 Service
public class ProductService {

    @RateLimiter(name = "product-read")      ← 方法級別，限制速率
    @Bulkhead(name = "product-read")         ← 方法級別，限制並發
    public List<GetProductListResponse> getProductList() { ... }
}
```

三者形成互補的防護體系：
- **RateLimiter**：防止突發流量超過系統設計容量
- **Bulkhead**：防止某個 API 佔用所有系統資源（隔離故障）
- **CircuitBreaker**：當下游持續失敗時，快速失敗避免雪崩

### 驗證方法

```bash
# 查看 CircuitBreaker 狀態
GET http://localhost:8787/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:ProductService

# 查看失敗率
GET http://localhost:8787/actuator/metrics/resilience4j.circuitbreaker.failure.rate?tag=name:ProductService
```

---

## 各 Service 的 RateLimiter 配置一覽

| Instance | limit-for-period | 適用方法 |
|----------|-----------------|----------|
| account-read | 500/s | AccountService 讀取操作 |
| account-write | 200/s | AccountService 寫入操作 |
| account-write-with-validation | 150/s | AccountService 帶驗證的寫入 |
| product-read | 1000/s | ProductService 讀取操作 |
| product-write | 200/s | ProductService 寫入操作 |
| product-inventory | 100/s | ProductService 庫存操作 |
| order-read | 300/s | OrderService 讀取操作 |
| order-write | 100/s | OrderService 寫入操作 |

所有 RateLimiter 實例都使用相同的機制，只要實際到達速率超過各自的 `limit-for-period`，就會觸發限流返回 HTTP 429。

---

## 附錄：負載測試工具建議

若要有效觸發 RateLimiter，建議：

1. **測試輕量 endpoint**（不查 DB），讓 throughput 能超過閾值
2. **使用更高效的工具**：如 `wrk`、`k6`、`Gatling`，它們比 JMeter 能產生更高的並發
3. **降低閾值**：測試時暫時將 `limit-for-period` 改小（如 10），驗證後改回

---

**最後更新**: 2025-06-06
**維護者**: Bobby
