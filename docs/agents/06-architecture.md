## 架構層次規範

### 專案分層架構

```
呼叫方 (外部請求端: browser / curl / 另一服務)
    │  inbound HTTP
    ↓
Controller (HTTP 端點)
    ↓
Service (業務邏輯) ──── outbound ───▶ *Client (跨模組呼叫元件: AccountClient / ProductClient / OrderClient)
    ↓                                     │ 底層為 RestClient;baseUrl 指向本應用 (loopback)
Repository (資料存取)                      └─▶ 自呼叫繞回本應用,成為「新的 inbound 請求」重走 filter chain
    ↓
Entity (資料模型)

Util (跨層工具類別: AuditMetadata, SoftDeleteMetadata, PageResponse, ServiceValidator, ErrorCode 等)
```

> **關於「Client」一詞的兩種意義**（避免混淆）：
> - **呼叫方 (caller)**：發請求進來的外部端(browser / curl / 另一服務),站在 inbound 鏈的最上方入口。
> - **`*Client` 元件**：`AccountClient` 等以 `RestClient` 支撐的**跨模組呼叫介面**,是 **Service 層往外送的 outbound 協作者**（由 Service 呼叫,而非位於 Controller 之前）。
>
> 兩者方向相反：呼叫方由上而下進入本應用；`*Client` 由 Service 往外送出。本專案 `*Client` 的 baseUrl 指向自己(loopback),因此 outbound 送出後會以一個新的 inbound 請求繞回 filter chain（見下方「安全」段落與 `RestClientConfig`）。

### Controller 層

- 僅負責請求映射與參數驗證 (`@Valid`)
- 不包含業務邏輯
- 使用 `ResponseEntity<T>` 明確控制 HTTP 回應

### Service 層

- 核心業務邏輯所在
- 使用 `@Transactional` 管理事務
- 查詢方法標註 `@Transactional(readOnly = true)` 提升效能
- 拋出 `BusinessException` 並帶入對應的 `ErrorCode`（`new BusinessException(ErrorCode.X, "...")`）

### Repository 層

- 繼承 `JpaRepository` 或 `SoftDeleteRepository`
- 優先使用方法名衍生查詢
- 複雜查詢使用 `@Query` (JPQL 優先於 Native SQL)

### Entity 層

- 以 `@Embedded` 組合 `AuditMetadata` / `SoftDeleteMetadata` 獲得審計欄位與軟刪除支援；`@Version` 樂觀鎖欄位直接定義在 entity（JPA 不支援 `@Version` 在 `@Embeddable`）
- 使用 `@Builder` 支援建構者模式（改用組合後不再需要 `@SuperBuilder`）
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
- **授權規則**：`anyRequest().authenticated()`；放行以下端點：
  - actuator：`/actuator/health/**`——給 Dockerfile HEALTHCHECK 的 `wget` 探針用（該探針也走此 filter chain，不放行會 401）。監控走 **OTLP push**，無需放行 scrape 端點。
  - springdoc：`/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html`（執行時免登入瀏覽 Swagger）。
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
        .requestMatchers("/actuator/health/**").permitAll()
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .anyRequest().authenticated())
    .httpBasic(Customizer.withDefaults());
```
