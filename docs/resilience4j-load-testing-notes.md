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

### 配置 (application.yml)

為了驗證 Bulkhead 的 fail-fast 行為，將並發限制設為較小值以利測試：

```yaml
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 5    # 限制同時執行請求數為 5
        max-wait-duration: 0ms     # fail-fast: 不等待，立即拒絕
```

### 測試場景與 JMeter 設定

- **目標 API**: `GET /product`
- **JMeter 參數**:
  - Number of Threads: 20
  - Ramp-up Period: 0s
  - Loop Count: 1
- **驗證邏輯**: 設定 20 個線程瞬間啟動，並發請求數必定超過 `max-concurrent-calls: 5`，預期會觸發 Bulkhead 拒絕機制。

### 測試結果與解讀

**執行結果**:
| Label | # Samples | Average | Error % | Throughput |
|-------|-----------|---------|---------|------------|
| HTTP Request | 20 | 106ms | 15.00% | 97.08 req/s |

**解讀**:
1. **Error % (15%)**: 20 個並發請求中有約 3 個觸發了 `BulkheadFullException`。
2. **HTTP 狀態碼**: 失敗請求均回傳 **503 Service Unavailable**，符合 Bulkhead 預期。
3. **成功與失敗機制**: 由於 `Average Response Time` (106ms) 很短，線程會快速釋放，因此後面進來的請求有機會搶到 slot，導致錯誤率未達 75% (15/20)。
4. **Exception 處理**: 透過更新 `GlobalExceptionHandler`，成功攔截錯誤並回傳具體資訊（如 `Bulkhead 'product-read' is full...`），確認 Bulkhead 攔截邏輯正確。

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

### 驗證方法

```bash
GET http://localhost:8787/actuator/metrics/resilience4j.bulkhead.available.concurrent.calls?tag=name:product-read
```

---

## CircuitBreaker

### 使用時機

CircuitBreaker 的設計目的是**保護呼叫方**，當被呼叫的下游服務持續故障時，避免呼叫方不斷重試造成：
1. **資源耗盡**：線程池、連接池被等待超時的請求佔滿
2. **雪崩效應**：一個服務故障拖垮整條呼叫鏈
3. **延遲放大**：每個請求都要等到超時才失敗，用戶體驗極差

**核心邏輯：「如果下游一直失敗，就別再打了，直接快速失敗，給下游喘息的時間恢復。」**

在本專案中，`OrderService` 透過 HTTP Client 呼叫了 `AccountService` 和 `ProductService`，當這些下游服務持續故障時，CircuitBreaker 會開路保護 OrderService 不被拖垮。

### 配置（生產環境 default）

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        register-health-indicator: true
        sliding-window-type: COUNT_BASED
        sliding-window-size: 100
        minimum-number-of-calls: 50
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 10
        automatic-transition-from-open-to-half-open-enabled: true
```

### 測試用配置（調小參數以利驗證）

為了方便在 JMeter 中觸發 CircuitBreaker，將 `OrderService` 的參數調小：

```yaml
resilience4j:
  circuitbreaker:
    instances:
      OrderService:
        base-config: default
        sliding-window-size: 10              # 只看最近 10 個請求（生產: 100）
        minimum-number-of-calls: 5           # 5 個請求就開始計算（生產: 50）
        failure-rate-threshold: 50           # 維持 50%
        wait-duration-in-open-state: 30s     # 縮短為 30 秒（生產: 60s）
        permitted-number-of-calls-in-half-open-state: 3  # 改小方便觀察（生產: 10）
```

### 測試場景

- **API**: `GET /order/99999`（查詢不存在的訂單 ID，觸發 `ResourceNotFoundException`）
- **工具**: JMeter
- **驗證邏輯**: OrderService 呼叫 `findOrderByIdOrThrow(99999)` 時拋出 `ResourceNotFoundException`，此異常被 CircuitBreaker 記錄為失敗。累積到閾值後 CircuitBreaker 開路，後續請求直接被拒絕。

> **為什麼選擇這個場景？** CircuitBreaker 不像 RateLimiter/Bulkhead 靠「大量並發」觸發，而是靠**持續的失敗**觸發。用不存在的 orderId 查詢是最簡單的方式來模擬「下游持續回傳錯誤」的效果。在真實生產環境中，更常見的觸發場景是下游服務掛掉（網路超時、連接拒絕、5xx 錯誤）。

### JMeter 設定

使用 **setUp Thread Group** 先觸發 CircuitBreaker 開路，再用正式 Thread Group 驗證開路後的行為。

#### 測試計畫結構

```
Test Plan
├── setUp Thread Group          ← 觸發 CircuitBreaker 開路
│   └── HTTP Request: GET /order/99999
│
└── Thread Group (正式測試)      ← 驗證開路後的行為
    └── HTTP Request: GET /order/99999
```

#### setUp Thread Group（觸發開路）

| 參數 | 值 | 說明 |
|------|-----|------|
| Number of Threads | 5 | 等於 `minimum-number-of-calls: 5` |
| Ramp-up Period | 0s | 同時啟動 |
| Loop Count | 1 | 每個線程發 1 次，共 5 個請求 |
| HTTP Request | `GET http://localhost:8787/order/99999` | 不存在的訂單 ID |

#### Thread Group（驗證開路）

| 參數 | 值 | 說明 |
|------|-----|------|
| Number of Threads | 10 | 10 個並發用戶 |
| Ramp-up Period | 0s | 同時啟動 |
| Loop Count | 1 | 每人發 1 次，共 10 個請求 |
| HTTP Request | `GET http://localhost:8787/order/99999` | 任何 OrderService 的 endpoint |

### 測試結果與解讀

#### setUp Thread Group 結果

| Label | # Samples | Average | Error % | HTTP Status |
|-------|-----------|---------|---------|-------------|
| HTTP Request | 5 | ~50ms | 100% | 404 Not Found |

**解讀**: 5 個請求全部觸發 `ResourceNotFoundException`，失敗率 = 100% > 50%，CircuitBreaker 在第 5 個請求後進入 **OPEN** 狀態。

#### Thread Group 結果

| Label | # Samples | Average | Error % | HTTP Status |
|-------|-----------|---------|---------|-------------|
| HTTP Request | 10 | < 5ms | 100% | 503 Service Unavailable |

**解讀**:
1. **回應時間驟降**：從 setUp 的 ~50ms 降至 < 5ms，因為請求根本沒有到達實際方法，被 CircuitBreaker 直接攔截。
2. **HTTP 503**: `CallNotPermittedException` 被 `GlobalExceptionHandler` 攔截，回傳 "服務暫時不可用，請稍後再試"。
3. **100% Error**: 所有請求都被 CircuitBreaker 拒絕，確認開路狀態正常運作。

### CircuitBreaker 狀態生命週期

```
CLOSED（正常）
    ↓ 失敗率 ≥ 50%（在 sliding-window-size 個請求中）
OPEN（開路，所有請求直接 503）
    ↓ 等待 wait-duration-in-open-state（30s）
HALF_OPEN（半開，允許 permitted-number-of-calls-in-half-open-state 個請求嘗試）
    ↓ 嘗試成功 → CLOSED
    ↓ 嘗試失敗 → OPEN
```

### 關鍵觀察：哪些異常會被記錄為失敗？

Resilience4j CircuitBreaker **預設會記錄所有 Exception 為失敗**（除非配置了 `record-exceptions` 或 `ignore-exceptions`）。本專案未配置這些，因此：

| 異常類型 | 是否被記錄為失敗 | 範例 |
|---------|----------------|------|
| `ResourceNotFoundException` | ✅ 是 | 查詢不存在的訂單/帳戶 |
| `AccountInactiveException` | ✅ 是 | 帳戶未啟用 |
| `InvalidRequestException` | ✅ 是 | 重複商品 |
| HTTP Client 異常（4xx/5xx） | ✅ 是 | ProductClient 回傳 404 |

> ⚠️ **生產環境建議**：考慮加上 `ignore-exceptions` 排除業務異常（如 `ResourceNotFoundException`），讓 CircuitBreaker 只對基礎設施故障（網路超時、連接拒絕、5xx）做反應。

### 行為說明

- CircuitBreaker 放在**類級別** (`@CircuitBreaker(name = "OrderService")`)
- 保護整個 Service 的所有方法，不管是哪個方法失敗都會累積到同一個 CircuitBreaker
- 當失敗率超過 50%（在最近 10 個請求中），電路斷開
- 斷開後等待 30 秒，進入半開狀態，允許 3 個請求嘗試
- 如果嘗試成功，電路關閉；如果仍然失敗，繼續斷開

### 與 RateLimiter/Bulkhead 的協作

```
@CircuitBreaker(name = "OrderService")       ← 類級別，保護整個 Service
public class OrderService {

    @RateLimiter(name = "order-read")        ← 方法級別，限制速率
    @Bulkhead(name = "order-read")           ← 方法級別，限制並發
    public GetOrderDetailResponse getOrderDetailByOrderId(Integer orderId) { ... }
}
```

三者形成互補的防護體系：
- **RateLimiter**：防止突發流量超過系統設計容量
- **Bulkhead**：防止某個 API 佔用所有系統資源（隔離故障）
- **CircuitBreaker**：當下游持續失敗時，快速失敗避免雪崩

執行順序（外到內）：`Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead → 實際方法`

### 驗證方法

```bash
# 查看 CircuitBreaker 狀態（CLOSED=0, OPEN=1, HALF_OPEN=2）
GET http://localhost:8787/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:OrderService

# 查看失敗率
GET http://localhost:8787/actuator/metrics/resilience4j.circuitbreaker.failure.rate?tag=name:OrderService

# 查看被拒絕的請求數
GET http://localhost:8787/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:OrderService&tag=kind:not_permitted
```

#### Grafana（透過 Prometheus）

```promql
# CircuitBreaker 狀態
resilience4j_circuitbreaker_state{name="OrderService", application="demo"}

# 失敗率趨勢
resilience4j_circuitbreaker_failure_rate{name="OrderService", application="demo"}

# 被拒絕的請求速率
rate(resilience4j_circuitbreaker_calls_total{name="OrderService", kind="not_permitted"}[1m])
```

### 結論

| 問題 | 答案 |
|------|------|
| CircuitBreaker 配置是否正確？ | ✅ 是 |
| CircuitBreaker AOP 是否生效？ | ✅ 是（開路後回傳 503） |
| 開路後回應時間是否驟降？ | ✅ 是（從 ~50ms 降至 < 5ms） |
| setUp Thread Group 能否有效觸發開路？ | ✅ 是（5 個失敗請求即可觸發） |

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

**最後更新**: 2025-06-08
**維護者**: Bobby
