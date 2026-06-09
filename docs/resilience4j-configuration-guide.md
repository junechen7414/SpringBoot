# 🛡️ Resilience4j 設定指南

> **最後更新**: 2026-06-03  
> **作者**: Bobby  
> **專案**: Spring Boot Demo Application

---

## 📋 目錄

1. [簡介與概述](#-簡介與概述)
2. [生產環境配置](#-生產環境配置)
3. [學習/演示配置](#-學習演示配置)
4. [配置比較表](#-配置比較表)
5. [JMeter 測試指南](#-jmeter-測試指南)
6. [故障排除指南](#-故障排除指南)
7. [快速參考](#-快速參考)

---

## 🎯 簡介與概述

### 什麼是 Resilience4j？

Resilience4j 是一個專為 Java 應用程式設計的輕量級容錯庫。本專案使用三種核心模式來保護我們的微服務：

### 三大模式

#### 1️⃣ **RateLimiter** - 請求速率控制
- **用途**: 限制每個時間段內的請求數量 (例如：每秒 1000 個請求)
- **應用場景**: 防止 API 濫用，保護後端免受流量激增影響
- **失敗回應**: HTTP 429 (Too Many Requests)
- **類比**: 像夜店的保全 - 每分鐘只允許 X 個人進入

#### 2️⃣ **Bulkhead** - 並發呼叫控制
- **用途**: 限制並行/並發執行的數量
- **應用場景**: 防止執行緒池耗盡，控制資源使用量
- **失敗回應**: HTTP 503 (Service Unavailable)
- **類比**: 像停車場 - 同時只能停 X 輛車

#### 3️⃣ **CircuitBreaker** - 級聯故障保護
- **用途**: 停止呼叫故障服務，防止故障級聯
- **應用場景**: 防止下游服務故障影響本服務
- **失敗回應**: HTTP 503 (Service Unavailable - Circuit Open)
- **類比**: 像電路斷路器 - 當發生過多故障時會跳閘

### 🔄 配置層級與執行順序

```
請求 → RateLimiter → Bulkhead → CircuitBreaker → 服務方法
           ↓              ↓            ↓
         429 錯誤      503 錯誤     503 錯誤
```

**執行流程**:
1. **RateLimiter** 首先檢查: "我們是否超過每秒請求限制了？"
2. **Bulkhead** 其次檢查: "是否有太多請求在並發執行？"
3. **CircuitBreaker** 第三檢查: "下游服務是否健康？"
4. 如果全部通過 → 執行實際的服務方法

### 🤔 何時使用哪種模式

| 模式 | 使用時機 | 不建議使用時機 |
|---------|----------|----------------|
| **RateLimiter** | • 保護公開 API<br>• 防止濫用<br>• 強制執行 SLA 限制 | • 僅內部服務<br>• 僅受信任的客戶端 |
| **Bulkhead** | • 資源有限 (資料庫連線、執行緒)<br>• 防止資源耗盡<br>• 隔離關鍵操作 | • 資源無限<br>• 簡單的 CRUD 操作 |
| **CircuitBreaker** | • 呼叫外部服務<br>• 下游依賴<br>• 網路操作 | • 本地操作<br>• 資料庫查詢 (請改用逾時設定) |

### 📊 模式關係

**關鍵規則**: `Bulkhead max-concurrent-calls >= RateLimiter limit-for-period`

**為什麼？**
- RateLimiter 允許每秒 X 個請求
- Bulkhead 必須能處理至少 X 個並發請求
- 否則，合法的請求會被 Bulkhead 拒絕

**範例**:
```yaml
ratelimiter:
  instances:
    api-read:
      limit-for-period: 1000        # 允許每秒 1000 個請求
bulkhead:
  instances:
    api-read:
      max-concurrent-calls: 1000    # 必須 >= 1000 才能全部處理
```

---

## 🏭 生產環境配置

### 配置原則

#### 1. 根據系統容量計算

**步驟 1: 測量系統容量**
```bash
# 使用負載測試工具找出：
# - 伺服器每秒可處理的最大請求數
# - 平均回應時間
# - 執行緒池大小
# - 資料庫連線池大小
```

**步驟 2: 應用安全邊際 (70-80% 原則)**
```
生產限制 = 測量容量 × 0.75
```

**範例計算**:
- 負載測試顯示伺服器可處理 1500 req/s，第 95 百分位回應時間 < 200ms
- 應用 75% 安全邊際: 1500 × 0.75 = **1125 req/s**
- 無條件捨去以保安全: **1000 req/s**

#### 2. 參數關係

**🔑 關鍵規則: Bulkhead ≥ RateLimiter**

Bulkhead 限制必須 **大於或等於** RateLimiter 限制。原因如下：

1. **執行順序**: RateLimiter 先檢查，然後是 Bulkhead
2. **如果 Bulkhead < RateLimiter**: Bulkhead 會成為瓶頸，並拒絕通過 RateLimiter 的合法請求
3. **RateLimiter 永遠不會觸發**: 您永遠看不到 429 錯誤，只會看到來自 Bulkhead 的 503 錯誤
4. **失去目的**: RateLimiter 的速率控制變得毫無意義

**問題範例**:
```
RateLimiter: 1000 req/s
Bulkhead: 500 並發

結果: 只能同時處理 500 個請求
→ RateLimiter 允許 1000/s，但 Bulkhead 阻擋 500/s
→ 您會看到 503 錯誤 (Bulkhead 滿載) 而非 429 (速率受限)
→ RateLimiter 配置被浪費
```

```yaml
# ✅ 正確配置
resilience4j:
  ratelimiter:
    instances:
      api-read:
        limit-for-period: 1000              # 每秒 1000 個請求
  bulkhead:
    instances:
      api-read:
        max-concurrent-calls: 1000          # >= RateLimiter 限制 ✅
        # Bulkhead 不會干擾 RateLimiter 的速率控制
```

```yaml
# ❌ 錯誤配置
resilience4j:
  ratelimiter:
    instances:
      api-read:
        limit-for-period: 1000              # 每秒 1000 個請求
  bulkhead:
    instances:
      api-read:
        max-concurrent-calls: 500           # < RateLimiter 限制 ❌
        # 問題: Bulkhead 成為瓶頸！
        # 通過 RateLimiter 的合法請求會被 Bulkhead 拒絕
        # 您永遠看不到 429 錯誤，只會看到 503 錯誤
```

**最佳實踐**:
- 設定 Bulkhead = RateLimiter (或略高)
- 確保 RateLimiter 控制速率，Bulkhead 保護資源
- 兩種模式皆正常運作而不相互干擾

#### 3. 不同 API 類型的配置

### 📖 讀取操作 (高吞吐量)

**特性**:
- 回應時間快 (< 100ms)
- 無資料修改
- 可處理高並發
- 可快取

**配置策略**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 1000              # 讀取的高限制
        limit-refresh-period: 1s
        timeout-duration: 0ms               # 快速失敗
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 1000          # 符合 RateLimiter
        max-wait-duration: 0ms              # 快速失敗
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 100            # 大視窗以保持穩定
        minimum-number-of-calls: 50         # 需要更多資料點
        failure-rate-threshold: 50          # 50% 故障率
        wait-duration-in-open-state: 60s    # 1 分鐘恢復時間
```

**理由**:
- **RateLimiter 1000/s**: 讀取操作快，可處理大流量
- **Bulkhead 1000**: 符合 RateLimiter 以避免不必要的拒絕
- **CircuitBreaker 視窗 100**: 較大視窗以進行更穩定的故障偵測
- **最少呼叫 50**: 開啟斷路器前需要足夠的資料

### ✍️ 寫入操作 (較低吞吐量)

**特性**:
- 回應時間較慢 (100-500ms)
- 資料修改 (資料庫寫入)
- 事務開銷
- 無法快取

**配置策略**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      product-write:
        limit-for-period: 200               # 寫入的較低限制
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      product-write:
        max-concurrent-calls: 200           # 符合 RateLimiter
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 50             # 較小視窗
        minimum-number-of-calls: 20         # 更快偵測
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s    # 較短恢復時間
```

**理由**:
- **RateLimiter 200/s**: 寫入操作慢，需要較低限制
- **Bulkhead 200**: 符合 RateLimiter，防止資料庫連線耗盡
- **CircuitBreaker 視窗 50**: 較小視窗以便更快偵測故障
- **最少呼叫 20**: 快速偵測寫入故障

### 🔧 關鍵操作 (嚴格控制)

**特性**:
- 複雜業務邏輯
- 多次驗證
- 外部服務呼叫
- 高資源消耗

**配置策略**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      order-create:
        limit-for-period: 100               # 極嚴格限制
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      order-create:
        max-concurrent-calls: 50            # 更嚴格的並發限制
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      OrderService:
        sliding-window-size: 30
        minimum-number-of-calls: 10         # 快速偵測
        failure-rate-threshold: 40          # 較低門檻 (40%)
        wait-duration-in-open-state: 60s
```

**理由**:
- **RateLimiter 100/s**: 複雜操作需要嚴格的速率限制
- **Bulkhead 50**: 低於 RateLimiter 以防止資源耗盡
- **CircuitBreaker 門檻 40%**: 對故障更敏感
- **最少呼叫 10**: 快速偵測關鍵操作的故障

### 📊 生產環境實際範例

#### 範例 1: 電子商務產品目錄 API

**系統規格**:
- 伺服器: 8 CPU 核心, 16GB RAM
- 資料庫: PostgreSQL, 100 連線池
- 負載測試結果: 2000 req/s 持續

**配置**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      # 產品列表 (高流量)
      product-list:
        limit-for-period: 1500              # 2000 × 0.75 = 1500
        limit-refresh-period: 1s
        timeout-duration: 0ms
      
      # 產品詳情 (中流量)
      product-detail:
        limit-for-period: 1000              # 低於列表
        limit-refresh-period: 1s
        timeout-duration: 0ms
      
      # 產品搜尋 (資源密集)
      product-search:
        limit-for-period: 500               # 更具限制性
        limit-refresh-period: 1s
        timeout-duration: 0ms
  
  bulkhead:
    instances:
      product-list:
        max-concurrent-calls: 1500          # 符合 RateLimiter
        max-wait-duration: 0ms
      
      product-detail:
        max-concurrent-calls: 1000
        max-wait-duration: 0ms
      
      product-search:
        max-concurrent-calls: 300           # 較低以減輕 DB 負載
        max-wait-duration: 0ms
  
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 100
        minimum-number-of-calls: 50
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 10
```

**計算分解**:
- **產品列表**: 簡單查詢，回應快 → 高限制 (1500/s)
- **產品詳情**: 單筆記錄抓取 → 中限制 (1000/s)
- **產品搜尋**: 全文搜尋，DB 密集 → 低限制 (500/s)
- **搜尋 Bulkhead**: 300 < 500 以防資料庫連線耗盡

#### 範例 2: 支付處理 API

**系統規格**:
- 伺服器: 4 CPU 核心, 8GB RAM
- 外部支付閘道 (第三方)
- 平均回應時間: 500ms

**配置**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      payment-process:
        limit-for-period: 100               # 外部呼叫需保守
        limit-refresh-period: 1s
        timeout-duration: 0ms
  
  bulkhead:
    instances:
      payment-process:
        max-concurrent-calls: 50            # 限制併發外部呼叫
        max-wait-duration: 0ms
  
  circuitbreaker:
    instances:
      PaymentService:
        sliding-window-size: 20             # 快速偵測的小視窗
        minimum-number-of-calls: 10
        failure-rate-threshold: 30          # 對故障敏感 (30%)
        wait-duration-in-open-state: 120s   # 外部服務 2 分鐘恢復時間
        permitted-number-of-calls-in-half-open-state: 5
        slow-call-duration-threshold: 2s    # >2s 視為慢呼叫
        slow-call-rate-threshold: 50        # 50% 慢呼叫將開啟斷路器
```

**理由**:
- **低 RateLimiter (100/s)**: 外部服務有速率限制
- **較低 Bulkhead (50)**: 防止過多併發外部呼叫
- **敏感 CircuitBreaker (30%)**: 保護免受支付閘道故障影響
- **較長等待時間 (120s)**: 給予外部服務更多恢復時間
- **慢呼叫偵測**: 防止超時級聯

### ✅ 生產環境最佳實踐

#### 1. 從保守開始，再進行優化
```yaml
# 階段 1: 初始部署 (保守)
ratelimiter:
  instances:
    api:
      limit-for-period: 500                 # 低限制開始

# 階段 2: 監控後 (優化)
ratelimiter:
  instances:
    api:
      limit-for-period: 1000                # 根據指標增加
```

#### 2. 監控與調整
```yaml
# 加入監控指標
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus

# 監控這些指標:
# - resilience4j.ratelimiter.available.permissions
# - resilience4j.bulkhead.available.concurrent.calls
# - resilience4j.circuitbreaker.state
```

#### 3. 不同環境使用不同配置
```yaml
# application-prod.yml
resilience4j:
  ratelimiter:
    instances:
      api:
        limit-for-period: 1000              # 生產限制

# application-staging.yml
resilience4j:
  ratelimiter:
    instances:
      api:
        limit-for-period: 500               # 測試環境較低

# application-dev.yml
resilience4j:
  ratelimiter:
    instances:
      api:
        limit-for-period: 100               # 開發環境極低
```

#### 4. 記錄配置計算過程
```yaml
# 務必加上註解解釋配置原因
resilience4j:
  ratelimiter:
    instances:
      product-read:
        # 計算: 負載測試顯示 1500 req/s 容量
        # 應用 75% 安全邊際: 1500 × 0.75 = 1125
        # 無條件捨去為: 1000 req/s
        # 最後更新: 2026-06-03
        # 負載測試日期: 2026-05-15
        limit-for-period: 1000
```

#### 5. 快速失敗哲學 (Fail-Fast)
```yaml
# ✅ 推薦: 快速失敗 (timeout-duration: 0ms)
resilience4j:
  ratelimiter:
    instances:
      api:
        timeout-duration: 0ms               # 不要等待，立即失敗
  bulkhead:
    instances:
      api:
        max-wait-duration: 0ms              # 不要佇列，立即失敗

# ❌ 不推薦: 等待/佇列
resilience4j:
  ratelimiter:
    instances:
      api:
        timeout-duration: 5s                # 等待會導致級聯延遲
```

**為什麼要快速失敗？**
- 防止級聯延遲
- 更好的使用者體驗 (快速失敗優於慢速超時)
- 易於偵錯
- 防止資源耗盡

---

## 🎓 學習/演示配置

### 目標
提供最小化配置，讓使用 JMeter 進行模式演示（約 20 個請求）變得 **容易**。

---

### A. RateLimiter 演示配置

#### 🎯 用途
演示請求速率限制 - 僅允許每秒 X 個請求。

#### ⚙️ 配置
```yaml
# 檔案: application.yml 或 application-demo.yml
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 10                # 每秒僅允許 10 個請求
        limit-refresh-period: 1s            # 每 1 秒重置
        timeout-duration: 0ms               # 超過限制立即失敗
```

#### 📊 JMeter 測試計畫

**Thread Group 配置**:
```
執行緒數 (使用者): 20
Ramp-Up 期間 (秒): 0                 # 所有執行緒立即啟動
迴圈計數: 1                               # 每個執行緒發送 1 個請求
```

**HTTP 請求**:
```
方法: GET
路徑: /api/products
```

**預期結果**:
- ✅ **~10 個請求成功** (HTTP 200)
- ❌ **~10 個請求失敗** (HTTP 429 - Too Many Requests)
- 429 回應內容: `{"error": "Rate limit exceeded"}`

#### 🔍 如何驗證

1. **結果樹檢視器**:
   - 綠色請求 (200): 成功
   - 紅色請求 (429): 速率受限

2. **匯總報告**:
   - 尋找 "錯誤 %" 約 50%
   - 吞吐量應為 ~10 個請求/秒

3. **回應斷言**:
   - 加入斷言檢查 HTTP 429
   - 驗證錯誤訊息包含 "Rate limit"

#### 📸 你應該看到的

```
請求 #1-10:  HTTP 200 ✅ (已允許)
請求 #11-20: HTTP 429 ❌ (速率受限)

回應時間: < 50ms (拒絕非常快)
```

---

### B. Bulkhead 演示配置

#### 🎯 用途
演示並發呼叫限制 - 僅允許 X 個並行執行。

#### ⚙️ 配置
```yaml
# 檔案: application.yml 或 application-demo.yml
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 15            # 僅允許 15 個並發請求
        max-wait-duration: 0ms              # 不要等待，立即拒絕
```

#### 📊 JMeter 測試計畫

**Thread Group 配置**:
```
執行緒數 (使用者): 30
Ramp-Up 期間 (秒): 0                 # 所有執行緒同時啟動
迴圈計數: 1
```

**HTTP 請求**:
```
方法: GET
路徑: /api/products
```

**⚠️ 關鍵: 加入固定計時器**:
```
計時器: 固定計時器 (Constant Timer)
執行緒延遲 (毫秒): 2000           # 使每個請求花費 2 秒
```

**為什麼需要計時器？**
- 若無計時器：請求完成太快，全部 30 個都可能放入 Bulkhead
- 有計時器：請求需 2 秒，因此同時只能有 15 個運行

**預期結果**:
- ✅ **~15 個請求成功** (HTTP 200) - 並發運行
- ❌ **~15 個請求失敗** (HTTP 503 - Service Unavailable)
- 503 回應內容: `{"error": "Bulkhead is full"}`

#### 🔍 如何驗證

1. **結果樹檢視器**:
   - 前 15 個請求: 綠色 (200)，回應時間 ~2000ms
   - 後 15 個: 紅色 (503)，回應時間 <50ms (立即拒絕)

2. **回應時間圖**:
   - 成功請求: ~2000ms (慢)
   - 被拒請求: <50ms (快速拒絕)

3. **匯總報告**:
   - 錯誤 % 約 50% (30 個中的 15 個)

#### 📸 你應該看到的

```
執行緒 1-15:  HTTP 200 ✅ (並發執行, ~2000ms)
執行緒 16-30: HTTP 503 ❌ (立即拒絕, <50ms)

並發呼叫: 15 (最大)
被拒呼叫: 15
```

---

### C. CircuitBreaker 演示配置

#### 🎯 用途
演示斷路器狀態：CLOSED → OPEN → HALF_OPEN → CLOSED

#### ⚙️ 配置
```yaml
# 檔案: application.yml 或 application-demo.yml
resilience4j:
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 10             # 演示用小視窗
        minimum-number-of-calls: 5          # 僅需 5 個呼叫即啟動
        failure-rate-threshold: 50          # 50% 故障率開啟斷路器
        wait-duration-in-open-state: 10s    # 恢復前等待 10 秒
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

#### 📊 JMeter 測試計畫 - 三個階段

#### **階段 1: 觸發開啟斷路器**

**Thread Group 1**:
```
執行緒數: 10
Ramp-Up 期間: 0
迴圈計數: 1
```

**HTTP 請求**:
```
方法: GET
路徑: /api/products/999999                  # 不存在的產品 (導致 404)
```

**預期結果**:
- 前 5-10 個請求: HTTP 404 (Not Found)
- 故障率達 50% 後斷路器開啟
- 後續請求: HTTP 503 (Circuit Open)

#### **階段 2: 等待半開啟 (HALF_OPEN)**

**等待 10 秒** (wait-duration-in-open-state)

**發生什麼事**:
- 斷路器自動轉換為 HALF_OPEN 狀態
- 準備測試服務是否已恢復

#### **階段 3: 透過成功請求關閉斷路器**

**Thread Group 2**:
```
執行緒數: 3
Ramp-Up 期間: 0
迴圈計數: 1
```

**HTTP 請求**:
```
方法: GET
路徑: /api/products/1                       # 有效產品 (導致 200)
```

**預期結果**:
- 所有 3 個請求: HTTP 200 (成功)
- 3 次成功呼叫後斷路器關閉
- 斷路器現在為 CLOSED 且健康

#### 🔍 如何驗證

1. **階段 1 - 斷路器開啟**:
   ```
   請求 1-5:  HTTP 404 ❌ (計入故障)
   請求 6:    HTTP 503 ⚠️ (斷路器 OPEN)
   請求 7-10: HTTP 503 ⚠️ (斷路器仍為 OPEN)
   ```

2. **階段 2 - 等待**:
   ```
   等待 10 秒...
   斷路器狀態: OPEN → HALF_OPEN
   ```

3. **階段 3 - 斷路器關閉**:
   ```
   請求 1-3: HTTP 200 ✅ (HALF_OPEN 狀態下的成功)
   斷路器狀態: HALF_OPEN → CLOSED
   ```

#### 📸 你應該看到的

**斷路器狀態時間軸**:
```
時間 0s:    CLOSED (健康)
時間 1s:    OPEN (故障過多)
時間 11s:   HALF_OPEN (測試恢復)
時間 12s:   CLOSED (已恢復)
```

**回應代碼**:
```
階段 1: 404, 404, 404, 404, 404, 503, 503, 503, 503, 503
階段 2: (等待...)
階段 3: 200, 200, 200
```

---

### D. 組合演示配置

#### 🎯 用途
展示所有三種模式組合運作，並具備正確的參數關係。

#### ⚙️ 配置
```yaml
# 檔案: application-demo.yml
resilience4j:
  # 模式 1: RateLimiter (優先檢查)
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 20                # 允許每秒 20 個請求
        limit-refresh-period: 1s
        timeout-duration: 0ms
  
  # 模式 2: Bulkhead (次之檢查)
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 25            # 允許 25 個並發 (≥ RateLimiter)
        max-wait-duration: 0ms
  
  # 模式 3: CircuitBreaker (最後檢查)
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 15s
        permitted-number-of-calls-in-half-open-state: 5
```

#### 📊 JMeter 測試計畫 - 組合測試

**Thread Group**:
```
執行緒數: 30
Ramp-Up 期間: 0
迴圈計數: 1
```

**HTTP 請求**:
```
方法: GET
路徑: /api/products
```

**加入固定計時器**:
```
執行緒延遲: 1000ms                        # 每個請求 1 秒
```

#### 🔍 預期結果

**請求流**:
```
請求 1-20:  通過 RateLimiter ✅
               → 20 個通過 Bulkhead ✅ (Bulkhead 限制為 25)
               → 20 個全部成功 ✅ (HTTP 200)

請求 21-30: 被 RateLimiter 拒絕 ❌ (HTTP 429)
```

**回應分佈**:
- ✅ **20 個請求**: HTTP 200 (成功 - 通過 RateLimiter 與 Bulkhead)
- ❌ **10 個請求**: HTTP 429 (速率受限)

#### 📸 你應該看到的

```
執行順序:
┌─────────────┐
│ RateLimiter │ → 20 通過, 10 被拒 (429)
└──────┬──────┘
       ↓
┌─────────────┐
│  Bulkhead   │ → 20 通過 (限制為 25)
└──────┬──────┘
       ↓
┌─────────────┐
│CircuitBreaker│ → 20 通過 (斷路器已關閉)
└──────┬──────┘
       ↓
   服務方法

---

## 📊 配置比較表

### 表 1: 生產環境 vs 學習配置

| 參數 | 生產值 | 學習值 | 學習值原因 |
|-----------|-----------------|----------------|---------------------------|
| **RateLimiter** |
| `limit-for-period` | 1000/s | 10/s | 20 個請求易於觸發 |
| `limit-refresh-period` | 1s | 1s | 同上 (標準間隔) |
| `timeout-duration` | 0ms | 0ms | 同上 (快速失敗) |
| **Bulkhead** |
| `max-concurrent-calls` | 1000 | 15 | 30 個執行緒易於看到拒絕 |
| `max-wait-duration` | 0ms | 0ms | 同上 (快速失敗) |
| **CircuitBreaker** |
| `sliding-window-size` | 100 | 10 | 演示用狀態快速變化 |
| `minimum-number-of-calls` | 50 | 5 | 少數請求即可快速觸發 |
| `failure-rate-threshold` | 50% | 50% | 同上 (標準門檻) |
| `wait-duration-in-open-state` | 60s | 10s | 演示用較短等待時間 |
| `permitted-calls-in-half-open` | 10 | 3 | 關閉斷路器需較少請求 |

### 表 2: 讀取 vs 寫入操作

| 方面 | 讀取操作 | 寫入操作 | 原因 |
|--------|----------------|------------------|-----------|
| **典型回應時間** | 50-100ms | 200-500ms | 寫入涉及資料庫事務 |
| **RateLimiter 限制** | 1000/s | 200/s | 讀取快，可處理更多 |
| **Bulkhead 並發** | 1000 | 200 | 符合 RateLimiter，防止 DB 耗盡 |
| **CircuitBreaker 視窗** | 100 | 50 | 讀取視窗大以保持穩定 |
| **最少呼叫** | 50 | 20 | 讀取需更多資料，寫入需快速偵測 |
| **故障門檻** | 50% | 50% | 同上 |
| **等待時間** | 60s | 30s | 寫入需較短等待 (需更快恢復) |
| **資源影響** | 低 (CPU) | 高 (DB, 鎖) | 寫入消耗更多資源 |
| **可快取性** | 是 | 否 | 讀取可快取 |

### 表 3: API 端點類型

| 端點類型 | RateLimiter | Bulkhead | CircuitBreaker | 範例 |
|--------------|-------------|----------|----------------|---------|
| **公開列表 API** | 1500/s | 1500 | 視窗: 100 | `GET /api/products` |
| **公開詳情 API** | 1000/s | 1000 | 視窗: 100 | `GET /api/products/{id}` |
| **搜尋 API** | 500/s | 300 | 視窗: 50 | `GET /api/products/search` |
| **建立 API** | 200/s | 200 | 視窗: 50 | `POST /api/products` |
| **更新 API** | 200/s | 200 | 視窗: 50 | `PUT /api/products/{id}` |
| **刪除 API** | 100/s | 100 | 視窗: 30 | `DELETE /api/products/{id}` |
| **批次 API** | 50/s | 20 | 視窗: 20 | `POST /api/products/batch` |
| **外部 API 呼叫** | 100/s | 50 | 視窗: 20, 門檻: 30% | 支付閘道 |

### 表 4: 環境特定配置

| 環境 | RateLimiter | Bulkhead | CircuitBreaker | 用途 |
|-------------|-------------|----------|----------------|---------|
| **生產** | 1000/s | 1000 | 視窗: 100, 等待: 60s | 真實流量, 高容量 |
| **預發布** | 500/s | 500 | 視窗: 50, 等待: 30s | 發布前測試 |
| **開發** | 100/s | 100 | 視窗: 20, 等待: 10s | 本地開發 |
| **負載測試** | 2000/s | 2000 | 視窗: 200, 等待: 5s | 壓力測試 |
| **演示/學習** | 10/s | 15 | 視窗: 10, 等待: 10s | 易於演示 |

### 表 5: 故障回應代碼

| 模式 | HTTP 狀態 | 回應內容 | 何時發生 | 客戶端動作 |
|---------|-------------|---------------|-----------------|---------------|
| **RateLimiter** | 429 | `{"error": "Rate limit exceeded"}` | 每秒請求過多 | 1 秒後重試 |
| **Bulkhead** | 503 | `{"error": "Bulkhead is full"}` | 並發呼叫過多 | 立即或稍後重試 |
| **CircuitBreaker (開啟)** | 503 | `{"error": "Circuit breaker is open"}` | 故障過多 | 等待斷路器關閉 |
| **CircuitBreaker (半開啟)** | 503 | `{"error": "Circuit breaker is half-open"}` | 測試恢復中 | 等待斷路器關閉 |
| **服務錯誤** | 500 | `{"error": "Internal server error"}` | 實際服務失敗 | 回報給技術支援 |

---

## 🧪 JMeter 測試指南

### 先決條件

1. **安裝 JMeter**: 從 [Apache JMeter](https://jmeter.apache.org/) 下載
2. **啟動應用程式**: `./gradlew bootRun`
3. **驗證應用程式**: `curl http://localhost:8080/actuator/health`

### JMeter 一般設定

#### 建立測試計畫
1. 開啟 JMeter
2. 右鍵點選 "Test Plan" → Add → Threads (Users) → Thread Group
3. 右鍵點選 "Thread Group" → Add → Sampler → HTTP Request
4. 右鍵點選 "Thread Group" → Add → Listener → View Results Tree
5. 右鍵點選 "Thread Group" → Add → Listener → Summary Report

---

### 測試 1: RateLimiter 測試

#### 🎯 目標
驗證每秒僅允許 10 個請求。

#### 📋 步驟說明

**步驟 1: 配置應用程式**
```yaml
# application.yml
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0ms
```

**步驟 2: 重啟應用程式**
```bash
./gradlew bootRun
```

**步驟 3: 配置 JMeter Thread Group**
```
名稱: RateLimiter Test
執行緒數 (使用者): 20
Ramp-Up 期間 (秒): 0
迴圈計數: 1
```

**步驟 4: 配置 HTTP 請求**
```
協定: http
伺服器名稱或 IP: localhost
連接埠號: 8080
HTTP 請求方法: GET
路徑: /api/products
```

**步驟 5: 加入回應斷言 (選用)**
```
右鍵點選 Thread Group → Add → Assertions → Response Assertion
測試欄位: Response Code
匹配規則: Matches
測試模式: 200|429
```

**步驟 6: 執行測試**
1. 點選綠色 "Start" 按鈕 (▶️)
2. 查看 "View Results Tree"

#### ✅ 預期結果

**結果樹檢視器**:
```
✅ 請求 1:  HTTP 200 - 回應時間: ~50ms
✅ 請求 2:  HTTP 200 - 回應時間: ~50ms
...
✅ 請求 10: HTTP 200 - 回應時間: ~50ms
❌ 請求 11: HTTP 429 - 回應時間: ~10ms
❌ 請求 12: HTTP 429 - 回應時間: ~10ms
...
❌ 請求 20: HTTP 429 - 回應時間: ~10ms
```

**匯總報告**:
```
標籤           樣本    平均    錯誤 %  吞吐量
GET /products    20      30ms     50%     20/秒
```

**回應內容 (429)**:
```json
{
  "timestamp": "2026-06-03T06:00:00.000+00:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded",
  "path": "/api/products"
}
```

#### 🔍 如何驗證成功

1. **檢查錯誤率**: 應為 ~50% (10 個成功，10 個失敗)
2. **檢查回應代碼**: 200 和 429 的混合
3. **檢查回應時間**: 429 回應應非常快 (<50ms)
4. **檢查日誌**: 尋找 "Rate limit exceeded" 訊息

---

### 測試 2: Bulkhead 測試

#### 🎯 目標
驗證僅允許 15 個並發請求。

#### 📋 步驟說明

**步驟 1: 配置應用程式**
```yaml
# application.yml
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 15
        max-wait-duration: 0ms
```

**步驟 2: 重啟應用程式**

**步驟 3: 配置 JMeter Thread Group**
```
名稱: Bulkhead Test
執行緒數 (使用者): 30
Ramp-Up 期間 (秒): 0
迴圈計數: 1
```

**步驟 4: 配置 HTTP 請求**
```
協定: http
伺服器名稱或 IP: localhost
連接埠號: 8080
HTTP 請求方法: GET
路徑: /api/products
```

**步驟 5: ⚠️ 關鍵 - 加入固定計時器**
```
右鍵點選 Thread Group → Add → Timer → Constant Timer
名稱: Slow Request Simulator
執行緒延遲 (毫秒): 2000
```

**為什麼需要計時器？**
- 若無計時器：請求在 ~50ms 內完成，全部 30 個都可能放入
- 有計時器：請求花費 2000ms，僅 15 個可並發運行

**步驟 6: 執行測試**

#### ✅ 預期結果

**結果樹檢視器**:
```
✅ 執行緒 1:  HTTP 200 - 回應時間: ~2000ms (運行中)
✅ 執行緒 2:  HTTP 200 - 回應時間: ~2000ms (運行中)
...
✅ 執行緒 15: HTTP 200 - 回應時間: ~2000ms (運行中)
❌ 執行緒 16: HTTP 503 - 回應時間: ~10ms (被拒)
...
❌ 執行緒 30: HTTP 503 - 回應時間: ~10ms (被拒)
```

**匯總報告**:
```
標籤           樣本    平均      錯誤 %  吞吐量
GET /products    30     ~1000ms    50%     ~7.5/秒
```

**回應內容 (503)**:
```json
{
  "timestamp": "2026-06-03T06:00:00.000+00:00",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Bulkhead is full",
  "path": "/api/products"
}
```

#### 🔍 如何驗證成功

1. **檢查錯誤率**: 應為 ~50% (15 個成功，15 個失敗)
2. **檢查回應時間**:
   - 成功 (200): ~2000ms (慢)
   - 失敗 (503): <50ms (快速拒絕)
3. **檢查並發呼叫**: 最多 15 個同時運行
4. **檢查日誌**: 尋找 "Bulkhead is full" 訊息

---

### 測試 3: CircuitBreaker 測試

#### 🎯 目標
演示斷路器狀態轉換：CLOSED → OPEN → HALF_OPEN → CLOSED

#### 📋 步驟說明

**步驟 1: 配置應用程式**
```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

**步驟 2: 重啟應用程式**

#### 階段 1: 觸發斷路器開啟

**步驟 3: 建立 Thread Group 1**
```
名稱: Phase 1 - Trigger Failures
執行緒數: 10
Ramp-Up 期間: 0
迴圈計數: 1
```

**步驟 4: 配置 HTTP 請求**
```
協定: http
伺服器名稱或 IP: localhost
連接埠號: 8080
HTTP 請求方法: GET
路徑: /api/products/999999              # 不存在的產品
```

**步驟 5: 執行階段 1**
1. 點選 "Start" 按鈕
2. 觀察故障率達 50% 後斷路器開啟

#### ✅ 階段 1 預期結果

**結果樹檢視器**:
```
❌ 請求 1-5:  HTTP 404 (Not Found) - 計入故障
⚠️ 請求 6-10: HTTP 503 (Circuit Open) - 斷路器已開啟
```

**斷路器狀態**: CLOSED → OPEN

#### 階段 2: 等待半開啟

**步驟 6: 等待 10 秒**
- 斷路器自動轉換為 HALF_OPEN
- 檢查應用程式日誌: "CircuitBreaker 'ProductService' changed state from OPEN to HALF_OPEN"

#### 階段 3: 關閉斷路器

**步驟 7: 建立 Thread Group 2**
```
名稱: Phase 3 - Successful Requests
執行緒數: 3
Ramp-Up 期間: 0
迴圈計數: 1
```

**步驟 8: 配置 HTTP 請求**
```
協定: http
伺服器名稱或 IP: localhost
連接埠號: 8080
HTTP 請求方法: GET
路徑: /api/products/1                   # 有效產品
```

**步驟 9: 執行階段 3**

#### ✅ 階段 3 預期結果

**結果樹檢視器**:
```
✅ 請求 1-3: HTTP 200 (成功) - 斷路器測試恢復中
```

**斷路器狀態**: HALF_OPEN → CLOSED

#### 🔍 完整測試驗證

**時間軸**:
```
時間 0s:    斷路器 CLOSED (健康)
時間 1s:    發送 10 個請求至 /products/999999
時間 2s:    斷路器 OPEN (達到 50% 故障率)
時間 12s:   斷路器 HALF_OPEN (自動轉換)
時間 13s:   發送 3 個請求至 /products/1
時間 14s:   斷路器 CLOSED (恢復成功)
```

**回應代碼順序**:
```
階段 1: 404, 404, 404, 404, 404, 503, 503, 503, 503, 503
階段 2: (等待 10 秒)
階段 3: 200, 200, 200
```

---

### 測試 4: 組合模式測試

#### 🎯 目標
測試所有三種模式共同運作。

#### 📋 步驟說明

**步驟 1: 配置應用程式**
```yaml
# application.yml
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 20
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 10
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 15s
```

**步驟 2: 配置 JMeter**
```
Thread Group:
  執行緒數: 30
  Ramp-Up 期間: 0
  迴圈計數: 1

HTTP 請求:
  路徑: /api/products

固定計時器:
  執行緒延遲: 1000ms
```

**步驟 3: 執行測試**

#### ✅ 預期結果

**請求分佈**:
```
總請求數: 30

RateLimiter 檢查:
  ✅ 通過: 20 個請求 (在每秒 20 個限制內)
  ❌ 失敗: 10 個請求 (HTTP 429 - 速率受限)

Bulkhead 檢查 (針對通過 RateLimiter 的 20 個):
  ✅ 通過: 10 個請求 (在並發限制內)
  ❌ 失敗: 10 個請求 (HTTP 503 - Bulkhead 已滿)

CircuitBreaker 檢查 (針對通過 Bulkhead 的 10 個):
  ✅ 通過: 10 個請求 (斷路器 CLOSED)
  ❌ 失敗: 0 個請求 (無故障)

最終結果:
  ✅ 成功: 10 個請求 (HTTP 200)
  ❌ 速率受限: 10 個請求 (HTTP 429)
  ❌ Bulkhead 已滿: 10 個請求 (HTTP 503)
```

**匯總報告**:
```
標籤           樣本    平均    錯誤 %  吞吐量
GET /products    30     ~350ms    66%     ~10/秒
```

---

### JMeter 常見提示

#### 1. 測試間清除結果
```
Run → Clear All (Ctrl+Shift+E)
```

#### 2. 儲存測試計畫
```
File → Save Test Plan As → resilience4j-test.jmx
```

#### 3. 查看詳細日誌
```
Options → Log Viewer
```

#### 4. 加入 HTTP Header 管理器
```
右鍵點選 Thread Group → Add → Config Element → HTTP Header Manager
加入: Content-Type: application/json
```

#### 5. 加入斷言
```
右鍵點選 HTTP Request → Add → Assertions → Response Assertion
```

#### 6. 匯出結果
```
右鍵點選 Listener → Save Table Data
```

---

## 🔧 故障排除指南

### 問題 1: 未見 429 錯誤 (RateLimiter)

#### 徵兆
- 所有請求返回 HTTP 200
- 未發生速率限制
- 錯誤率為 0%

#### 可能原因與解決方案

**原因 1: 配置未應用**
```yaml
# ❌ 錯誤實例名稱
resilience4j:
  ratelimiter:
    instances:
      wrong-name:                           # 不符合 @RateLimiter 註解
        limit-for-period: 10

# ✅ 正確實例名稱
resilience4j:
  ratelimiter:
    instances:
      product-read:                         # 符合 @RateLimiter("product-read")
        limit-for-period: 10
```

**原因 2: 應用程式未重啟**
```bash
# 解決方案: 重啟應用程式
./gradlew bootRun
```

**原因 3: 請求次數太少**
```
# 問題: 發送 5 個請求，限制為 10
# 解決方案: 發送多於限制的請求數
執行緒數: 20  (應 > limit-for-period)
```

**原因 4: 請求太慢**
```
# 問題: Ramp-up 期間將請求分散在一段時間內
Ramp-Up 期間: 10s  ❌ (請求分散在 10 秒內)

# 解決方案: 一次發送所有請求
Ramp-Up 期間: 0s   ✅ (立即發送)
```

**驗證步驟**:
1. 檢查應用程式日誌，尋找 "Rate limit exceeded"
2. 驗證註解: `@RateLimiter(name = "product-read")`
3. 檢查 Actuator: `curl http://localhost:8080/actuator/ratelimiters`
4. 將執行緒計數增加到限制的 2 倍

---

### 問題 2: 所有請求失敗 (Bulkhead)

#### 徵兆
- 所有請求返回 HTTP 503
- 錯誤率為 100%
- 無成功請求

#### 可能原因與解決方案

**原因 1: 缺少固定計時器**
```
# 問題: 請求完成太快
# 若無計時器: 全部 30 個請求在 <1 秒內完成
# 任何時間點只有 1-2 個並發

# 解決方案: 加入固定計時器
右鍵點選 Thread Group → Add → Timer → Constant Timer
執行緒延遲: 2000ms  (使請求變慢)
```

**原因 2: Bulkhead 太小**
```yaml
# ❌ 太嚴格
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 1             # 僅 1 個並發！

# ✅ 演示用合理值
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 15            # 15 個並發
```

**原因 3: 錯誤的參數關係**
```yaml
# ❌ Bulkhead < RateLimiter (導致拒絕)
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 100
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 10            # 太小！

# ✅ Bulkhead >= RateLimiter
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 100
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 100           # 符合 RateLimiter
```

**驗證步驟**:
1. 加入 2000ms 固定計時器
2. 檢查執行緒數 < max-concurrent-calls
3. 驗證部分請求顯示 ~2000ms 回應時間
4. 檢查 Actuator: `curl http://localhost:8080/actuator/bulkheads`

---

### 問題 3: 斷路器未開啟

#### 徵兆
- 斷路器保持 CLOSED
- 無 HTTP 503 錯誤
- 故障未觸發斷路器

#### 可能原因與解決方案

**原因 1: 故障次數不足**
```yaml
# 問題: 需要 50% 故障率且最少 5 個呼叫
# 發送僅 3 個請求且 2 個失敗 = 66% 但小於最小值

# 解決方案: 發送更多請求
minimum-number-of-calls: 5                  # 需要至少 5 個呼叫
# 發送至少 10 個請求並達到 50% 故障率
```

**原因 2: 錯誤的端點**
```
# ❌ 端點未觸發故障
路徑: /api/products                         # 返回 200 (成功)

# ✅ 會導致故障的端點
路徑: /api/products/999999                  # 返回 404 (故障)
```

**原因 3: 滑動視窗太大**
```yaml
# ❌ 視窗對於演示太大
resilience4j:
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 100            # 需要 50 個故障！
        minimum-number-of-calls: 50

# ✅ 演示用較小視窗
resilience4j:
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 10             # 僅需 5 個故障
        minimum-number-of-calls: 5
```

**原因 4: 錯誤的服務名稱**
```java
// ❌ 註解不符合配置
@CircuitBreaker(name = "WrongService")
public List<Product> getProducts() { }

// 配置
resilience4j:
  circuitbreaker:
    instances:
      ProductService:                       # 不匹配！

// ✅ 匹配名稱
@CircuitBreaker(name = "ProductService")
public List<Product> getProducts() { }
```

**驗證步驟**:
1. 對不存在的資源發送 10 個請求
2. 檢查日誌，尋找 "Circuit breaker opened"
3. 驗證故障率 > 門檻
4. 檢查 Actuator: `curl http://localhost:8080/actuator/circuitbreakers`

---

### 問題 4: 參數未生效

#### 徵兆
- 變更配置但行為不變
- 舊限制仍在套用
- 新值未運作

#### 可能原因與解決方案

**原因 1: 應用程式未重啟**
```bash
# 解決方案: 配置變更後務必重啟
./gradlew bootRun
```

**原因 2: 錯誤的配置檔案**
```bash
# 檢查哪個 Profile 處於活動狀態
# application.yml vs application-dev.yml vs application-prod.yml

# 解決方案: 驗證活動 Profile
spring:
  profiles:
    active: dev                             # 使用 application-dev.yml
```

**原因 3: 快取/舊狀態**
```bash
# 解決方案: 清除建置並重啟
./gradlew clean
./gradlew bootRun
```

**原因 4: YAML 縮排錯誤**
```yaml
# ❌ 錯誤縮排
resilience4j:
ratelimiter:                                # 應當縮排！
  instances:
    product-read:
      limit-for-period: 10

# ✅ 正確縮排
resilience4j:
  ratelimiter:                              # 正確縮排
    instances:
      product-read:
        limit-for-period: 10
```

**驗證步驟**:
1. 檢查應用程式啟動日誌的配置值
2. 使用在線驗證器驗證 YAML 語法
3. 檢查 Actuator 端點以取得當前配置
4. 加入日誌以查看載入的配置

---

### 問題 5: 結果不一致

#### 徵兆
- 有時運作正常，有時不正常
- 每次執行結果不同
- 行為不可預測

#### 可能原因與解決方案

**原因 1: 競爭條件**
```
# 問題: 執行緒啟動時間稍有不同
Ramp-Up 期間: 1s                          # 執行緒分散在 1 秒內

# 解決方案: 所有執行緒同時啟動
Ramp-Up 期間: 0s                          # 所有執行緒立即開始
```

**原因 2: 測試遺留狀態**
```bash
# 問題: 斷路器從上次測試中保持 OPEN
# 解決方案: 等待斷路器關閉或重啟應用程式

# 檢查斷路器狀態
curl http://localhost:8080/actuator/circuitbreakers

# 重啟應用程式
./gradlew bootRun
```

**原因 3: 基於時間的限制**
```yaml
# 問題: RateLimiter 每秒重置
# 若測試跨越多秒，結果會不同

# 解決方案: 在 1 秒內發送所有請求
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 10
        limit-refresh-period: 1s            # 每秒重置！

# JMeter: 使用 0 ramp-up 以一次發送
```

**驗證步驟**:
1. 執行間清除 JMeter 結果
2. 測試間重啟應用程式
3. 使用 0 ramp-up 期間
4. 檢查一致的錯誤率

---

### 問題 6: 錯誤的 HTTP 狀態代碼

#### 徵兆
- 得到 500 而非 429/503
- 意外的錯誤回應
- 錯誤的錯誤訊息

#### 可能原因與解決方案

**原因 1: 異常未處理**
```java
// ❌ 異常傳播為 500
@GetMapping("/products")
public List<Product> getProducts() {
    // RequestNotPermitted 異常 → 500 錯誤
}

// ✅ 全域異常處理器捕獲它
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
        RequestNotPermitted ex) {
        return ResponseEntity.status(429)   // 返回 429
            .body(new ApiErrorResponse("Rate limit exceeded"));
    }
}
```

**原因 2: 錯誤的異常類型**
```java
// 檢查拋出哪個異常:
// - RequestNotPermitted → RateLimiter (應為 429)
// - BulkheadFullException → Bulkhead (應為 503)
// - CallNotPermittedException → CircuitBreaker (應為 503)
```

**驗證步驟**:
1. 檢查 GlobalExceptionHandler.java
2. 驗證異常對應關係
3. 檢查應用程式日誌中的異常類型
4. 分別測試每一種模式

---

### 除錯指令

#### 檢查 Resilience4j 配置
```bash
# 查看所有 Actuator 端點
curl http://localhost:8080/actuator

# 檢查 RateLimiter 狀態
curl http://localhost:8080/actuator/ratelimiters

# 檢查 Bulkhead 狀態
curl http://localhost:8080/actuator/bulkheads

# 檢查 CircuitBreaker 狀態
curl http://localhost:8080/actuator/circuitbreakers

# 檢查健康狀態
curl http://localhost:8080/actuator/health
```

#### 啟用除錯日誌
```yaml
# application.yml
logging:
  level:
    io.github.resilience4j: DEBUG
    com.ibm.demo: DEBUG
```

#### 檢查指標
```bash
# Prometheus 指標
curl http://localhost:8080/actuator/prometheus | grep resilience4j
```

---

## 🚀 快速參考

### 快速開始 - 學習配置

**複製貼上即可用的演示配置**:

```yaml
# 檔案: application-demo.yml
resilience4j:
  # RateLimiter: 允許每秒 10 個請求
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0ms
      product-write:
        limit-for-period: 5
        limit-refresh-period: 1s
        timeout-duration: 0ms
  
  # Bulkhead: 允許 15 個並發呼叫 (≥ RateLimiter)
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 15
        max-wait-duration: 0ms
      product-write:
        max-concurrent-calls: 5
        max-wait-duration: 0ms
  
  # CircuitBreaker: 快速狀態變更
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

**JMeter 快速測試**:
```
Thread Group: 20 個執行緒, 0 ramp-up, 1 迴圈
HTTP 請求: GET http://localhost:8080/api/products
預期: ~10 成功 (200), ~10 失敗 (429)
```

---

### 快速開始 - 生產配置

**複製貼上即可用的生產配置**:

```yaml
# 檔案: application-prod.yml
resilience4j:
  # RateLimiter: 根據負載測試結果
  ratelimiter:
    configs:
      default:
        limit-refresh-period: 1s
        timeout-duration: 0ms
    instances:
      # 讀取操作 (高吞吐量)
      product-read:
        limit-for-period: 1000              # 1000 req/s
      account-read:
        limit-for-period: 1000
      order-read:
        limit-for-period: 500
      
      # 寫入操作 (低吞吐量)
      product-write:
        limit-for-period: 200               # 200 req/s
      account-write:
        limit-for-period: 200
      order-write:
        limit-for-period: 100
  
  # Bulkhead: 符合或超過 RateLimiter 限制
  bulkhead:
    configs:
      default:
        max-wait-duration: 0ms
    instances:
      # 讀取操作
      product-read:
        max-concurrent-calls: 1000          # >= RateLimiter
      account-read:
        max-concurrent-calls: 1000
      order-read:
        max-concurrent-calls: 500
      
      # 寫入操作
      product-write:
        max-concurrent-calls: 200
      account-write:
        max-concurrent-calls: 200
      order-write:
        max-concurrent-calls: 100
  
  # CircuitBreaker: 保護免受級聯故障影響
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
    instances:
      ProductService:
        base-config: default
      AccountService:
        base-config: default
      OrderService:
        base-config: default
```

---

### 測試清單

使用此清單確認每一種模式皆正確運作：

#### ✅ RateLimiter 驗證
- [ ] 配置 limit-for-period: 10
- [ ] 以 0 ramp-up 發送 20 個請求
- [ ] 驗證 ~10 個成功 (HTTP 200)
- [ ] 驗證 ~10 個失敗 (HTTP 429)
- [ ] 檢查錯誤訊息: "Rate limit exceeded"
- [ ] 驗證快速拒絕 (429 回應時間 <50ms)

#### ✅ Bulkhead 驗證
- [ ] 配置 max-concurrent-calls: 15
- [ ] 加入 2000ms 固定計時器
- [ ] 以 0 ramp-up 發送 30 個請求
- [ ] 驗證 ~15 個成功 (HTTP 200, ~2000ms)
- [ ] 驗證 ~15 個失敗 (HTTP 503, <50ms)
- [ ] 檢查錯誤訊息: "Bulkhead is full"

#### ✅ CircuitBreaker 驗證
- [ ] 配置 sliding-window-size: 10, minimum-calls: 5
- [ ] 階段 1: 發送 10 個請求至 /products/999999
- [ ] 驗證斷路器開啟 (HTTP 503)
- [ ] 階段 2: 等待 10 秒
- [ ] 驗證斷路器進入 HALF_OPEN
- [ ] 階段 3: 發送 3 個請求至 /products/1
- [ ] 驗證斷路器關閉 (HTTP 200)
- [ ] 檢查狀態變更日誌

#### ✅ 組合模式驗證
- [ ] 配置所有三種模式
- [ ] 以 1000ms 計時器發送 30 個請求
- [ ] 驗證 10 個成功 (HTTP 200)
- [ ] 驗證 10 個速率受限 (HTTP 429)
- [ ] 驗證 10 個 Bulkhead 已滿 (HTTP 503)
- [ ] 檢查執行順序: RateLimiter → Bulkhead → CircuitBreaker

---

### 配置範本

#### 範本 1: 公開 API (高流量)
```yaml
resilience4j:
  ratelimiter:
    instances:
      public-api:
        limit-for-period: 1500
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      public-api:
        max-concurrent-calls: 1500
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      PublicService:
        sliding-window-size: 100
        minimum-number-of-calls: 50
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
```

#### 範本 2: 內部 API (中流量)
```yaml
resilience4j:
  ratelimiter:
    instances:
      internal-api:
        limit-for-period: 500
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      internal-api:
        max-concurrent-calls: 500
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      InternalService:
        sliding-window-size: 50
        minimum-number-of-calls: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

#### 範本 3: 外部服務呼叫 (低流量，敏感)
```yaml
resilience4j:
  ratelimiter:
    instances:
      external-api:
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      external-api:
        max-concurrent-calls: 50
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      ExternalService:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 30          # 更敏感
        wait-duration-in-open-state: 120s   # 更長恢復時間
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
```

---

### 有用連結

- **當前配置**: [`src/main/resources/application.yml`](../src/main/resources/application.yml) (第 96-194 行)
- **Resilience4j 文件**: https://resilience4j.readme.io/
- **JMeter 下載**: https://jmeter.apache.org/download_jmeter.cgi
- **Spring Boot Actuator**: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html

---

### 關鍵要點

1. **🎯 模式順序**: RateLimiter → Bulkhead → CircuitBreaker
2. **📊 參數規則**: Bulkhead ≥ RateLimiter 限制
3. **⚡ 快速失敗**: 一律使用 0ms 逾時/等待以實現立即拒絕
4. **📈 從保守開始**: 以較低限制開始，根據監控增加
5. **🔍 監控所有事項**: 使用 Actuator 端點與指標
6. **🧪 單獨測試**: 組合前驗證每一種模式皆運作正常
7. **📝 記錄計算**: 務必解釋為何選擇特定數值
8. **🔄 環境特定**: 生產/預發布/開發環境使用不同配置
9. **⏱️ 回應時間**: 429/503 應 <50ms (快速拒絕)
10. **🛡️ 深度防禦**: 組合使用三種模式以達最佳保護

---

**指南結束** | 最後更新: 2026-06-06 | 維護者: Bobby