## 架構層次規範

### 專案分層架構

```
Client (跨模組呼叫介面)
    ↓
Controller (HTTP 端點)
    ↓
Service (業務邏輯)
    ↓
Repository (資料存取)
    ↓
Entity (資料模型)
```

### Controller 層

- 僅負責請求映射與參數驗證 (`@Valid`)
- 不包含業務邏輯
- 使用 `ResponseEntity<T>` 明確控制 HTTP 回應

### Service 層

- 核心業務邏輯所在
- 使用 `@Transactional` 管理事務
- 查詢方法標註 `@Transactional(readOnly = true)` 提升效能
- 拋出自定義業務異常（繼承 `BusinessException`）

### Repository 層

- 繼承 `JpaRepository` 或 `SoftDeleteRepository`
- 優先使用方法名衍生查詢
- 複雜查詢使用 `@Query` (JPQL 優先於 Native SQL)

### Entity 層

- 繼承 `BaseEntity` 獲得審計欄位與軟刪除支援
- 使用 `@SuperBuilder` 支援建構者模式
- 關聯關係標註 `@ToString.Exclude` 避免循環引用

### 併發控制與容錯

#### Resilience4j 配置

- **Bulkhead**: 限制併發數，`max-wait-duration: 0ms` 實現 fail-fast
- **Circuit Breaker**: 故障率達 50% 觸發熔斷，等待 30 秒後進入半開狀態
- **Rate Limiter**: 每秒限制 100 個請求

#### 使用方式

```java
@Service
public class ProductService {
    @CircuitBreaker(name = "productService", fallbackMethod = "fallbackMethod")
    @Bulkhead(name = "productService")
    public Product getProduct(Long id) {
        // 業務邏輯
    }
}
```
