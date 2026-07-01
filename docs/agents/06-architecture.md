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

Util (跨層工具類別: BaseEntity, PageResponse, ServiceValidator, ErrorCode 等)
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

### 分頁策略

- **統一分頁回應**: 所有列表查詢使用 `PageResponse<T>` 封裝，不提供非分頁列表端點
- **Controller 層**: 接收 `Pageable` 參數（`page`, `size`, `sort`）
- **Service 層**: 回傳 `Page<T>`，由 Controller 轉換為 `PageResponse<T>`
- **預設值**: `page=0`, `size=20`
- **詳細指南**: 參考 `docs/pagination-strategies-guide.md`

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

### 安全（Spring Security）

- **設定位置**：`config/SecurityConfig.java`，提供 **stateless HTTP Basic** 的 `SecurityFilterChain`。
- **授權規則**：`anyRequest().authenticated()`；放行監控與文件端點：
  - actuator：`/actuator/health/**`、`/actuator/info`（`/actuator/prometheus` 一併放行，但本專案監控走 **OTLP push**，實際沒有該 scrape 端點）。
  - springdoc：`/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html`。
- **使用者**：in-memory（`api`、`internal` 兩帳號），帳密來自 `app.auth.*`（env 覆寫；見 `AppProperties.Auth`）。**不建 DB 使用者表**。
- **分層界線**：Security filter chain 只攔 **inbound HTTP** —— Oracle/Hikari 連線、OTLP metric 推送、容器間網路都不受影響。
- **內部 `*Client` 自呼叫**：因 `*Client` 透過 loopback HTTP 打回本應用，`RestClientConfig` 為其掛上 `internal` 帳號的 Basic 憑證，讓自呼叫能通過 filter chain（否則會 401）。
- **openapi profile**：`@Profile("openapi")` 另有一條全 `permitAll` 的 chain，確保 `generateOpenApiDocs`（打 `/v3/api-docs`）不被擋。

```java
// 授權規則骨架
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .anyRequest().authenticated())
    .httpBasic(Customizer.withDefaults());
```
