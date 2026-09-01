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

Util (跨層工具類別: AuditMetadata, SoftDeleteMetadata, PageResponse, ServiceValidator 等)
Exception (例外與錯誤契約: BusinessException, SystemException, ErrorCode, ValidationError;
           ApiErrorResponse 僅為 OpenAPI schema 宣告,執行期實際寫出的是 Spring ProblemDetail)
```

> **關於「Client」一詞的兩種意義**（避免混淆）：
> - **呼叫方 (caller)**：發請求進來的外部端(browser / curl / 另一服務),站在 inbound 鏈的最上方入口。
> - **`*Client` 元件**：`AccountClient` 等以 `RestClient` 支撐的**跨模組呼叫介面**,是 **Service 層往外送的 outbound 協作者**（由 Service 呼叫,而非位於 Controller 之前）。
>
> 兩者方向相反：呼叫方由上而下進入本應用；`*Client` 由 Service 往外送出。本專案 `*Client` 的 baseUrl 指向自己(loopback),因此 outbound 送出後會以一個新的 inbound 請求繞回 filter chain（見下方「安全」段落與 `RestClientConfig`）。

### Controller 層

- 僅負責請求映射與參數驗證 (`@Valid`)
- 不包含業務邏輯
- 使用 `ResponseEntity<T>` 明確控制 HTTP 回應，且**走 HTTP 原生語意、不加成功信封**（沒有 `{"success": ..., "data": ...}` 這種外層）：
  - **建立資源** → `201 Created` + `Location` 標頭 + body `{"id": n}`，一律用 `util/CreatedResponse.at(id)` 組（`Location` 由目前請求 URI 推導，且不含 query string）
  - **成功但沒有內容可回**（更新／刪除／內部庫存變動／資格驗證）→ `204 No Content`，body 必須真的是空的
  - **成功且有內容** → `200 OK` + 具名 DTO 或 `PageResponse<T>`；**不回裸純量** —— 線路上的 `true` 或 `5` 沒有說自己是「什麼」，呼叫端只能靠文件外的默契解讀，且日後補第二個欄位就是破壞性變更（範例見 `order/DTO/internal/OrderExistenceResponse`）
  - 這三條由 `src/test/java/com/ibm/demo/contract/ApiSuccessContractTest.java` 釘住 —— 改回裸純量仍然編譯得過、單元測試也全綠，只有它會紅
- 錯誤側完全交給 `GlobalExceptionHandler`：controller 不寫 `try-catch`、不手組 error body。對外格式是 RFC 9457 `application/problem+json`（見 `docs/handout/03-exception-handling.md`）

### Service 層

- 核心業務邏輯所在
- 使用 `@Transactional` 管理事務
- 查詢方法標註 `@Transactional(readOnly = true)` 提升效能
- 業務失敗拋出 `BusinessException` 並帶入對應的 `ErrorCode`（`new BusinessException(ErrorCode.X, "...")`）
- 系統／整合失敗（下游壞了、非業務原因）拋出 `SystemException`，排查用資訊以 `.with(key, value)` 掛在 context 上，**不要**串進 message —— 500 的 message 不回給呼叫端
- **不要在 Service 記錄例外**：log 一律由 `GlobalExceptionHandler` 統一記錄，**等級由最終 HTTP status 決定**而非例外型別（500 → ERROR 帶 stack trace，其餘 → WARN 一行）

### Repository 層

- 繼承 `JpaRepository` 或 `SoftDeleteRepository`
- 優先使用方法名衍生查詢
- 複雜查詢使用 `@Query` (JPQL 優先於 Native SQL)
- Repository interface 不靠 `@Component` 建立：Spring Data 會透過 `JpaRepositoryFactoryBean`／repository factory 在執行期建立 proxy bean；標準 CRUD、衍生查詢與 `@Query` 都由該 proxy 分派。

### 宣告式 HTTP Client

- `AccountClient`、`ProductClient`、`OrderClient` 與 Repository 同樣只宣告 interface 契約，實際注入的是 framework 在執行期建立的 proxy bean，不需要自行撰寫 `Impl`，也不要加 `@Component`／`@Controller`。
- 兩者雖然都以 Spring AOP `ProxyFactory` 建立介面 proxy，卻是不同管線：Repository 走 Spring Data repository scanner／`JpaRepositoryFactoryBean`；HTTP Client 走 `@ImportHttpServices`／HTTP Service registry／`HttpServiceProxyFactory`。
- `@ImportHttpServices(group = "internal", types = {...})` 負責指定哪些 `@HttpExchange` interface 要註冊成 client bean；新增 `*Client` 時必須同步加入 `types`。
- `@HttpExchange` 與 `@GetExchange`／`@PostExchange` 描述 request 契約；`RestClientHttpServiceGroupConfigurer` 只設定 group 背後的 RestClient（base URL、request factory／共用連線池、Basic header、status handler），**不負責發現或註冊 client interface**。configurer 的 `forEachClient` callback 是**每個 group 跑一次**（一個 group 只有一個 `RestClient.Builder`），group 內所有 proxy 共用該 `RestClient` 與其連線池 —— 不是每個 client interface 各跑一次。
- configurer bean 的型別**必須**宣告成 `RestClientHttpServiceGroupConfigurer`：registry 是用 `HttpServiceGroupAdapter.getConfigurerType()` 去查 bean，泛型會被 erasure 抹掉。寫成 `HttpServiceGroupConfigurer<RestClient.Builder>` 能編譯但永遠不會被套用，且無任何錯誤訊息。
- 這套機制屬於 **Spring Framework 7**（`spring-web` 的 HTTP Service registry，`@since 7.0`）；Boot 4 額外提供的是 `spring.http.serviceclient.*` 屬性繫結與 `RestClientCustomizer` 橋接兩個 configurer bean。原本由應用程式手動撰寫的 `RestClientAdapter`／`HttpServiceProxyFactory.createClient(...)` 接線改由 framework 自動完成；底層仍使用這套 adapter 與 proxy factory，並非完全移除。

### Entity 層

- 以 `@Embedded` 組合 `AuditMetadata` / `SoftDeleteMetadata` 獲得審計欄位與軟刪除支援；`@Version` 樂觀鎖欄位直接定義在 entity（JPA 不支援 `@Version` 在 `@Embeddable`）
- 使用 `@Builder` 支援建構者模式（改用組合後不再需要 `@SuperBuilder`）
- 關聯關係標註 `@ToString.Exclude` 避免循環引用

### 分頁策略

- **統一分頁回應**: 所有列表查詢使用 `PageResponse<T>` 封裝，不提供非分頁列表端點
- **Controller 層**: 接收 `Pageable` 參數（`page`, `size`, `sort`），並標上 springdoc 的 `@ParameterObject`（不可用 `@Parameter`，否則 Swagger UI 會當成單一 object 參數而無法送出）
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
- **定位：這是佔位方案**，只夠服務對服務與教學用。正式環境應改為只當 OAuth2 Resource Server、把 authN/authZ 交給外部 IdP —— 遷移步驟見 **`docs/security-external-idp-migration.md`**。
- **授權規則**：`anyRequest().authenticated()`；放行以下端點：
  - actuator：`/actuator/health/**`——給 Dockerfile HEALTHCHECK 的 `wget` 探針用（該探針也走此 filter chain，不放行會 401）。監控走 **OTLP push**，無需放行 scrape 端點。
  - springdoc：`/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html`（執行時免登入瀏覽 Swagger）。
- **無方法級授權（authZ）**：`roles("API")` / `roles("INTERNAL")` 目前**沒有任何授權規則在用**（全專案無 `@PreAuthorize` / `hasRole`）。保留它們是作為未來映射 IdP role claim 的接縫，不代表現在有授權效果 —— internal 服務帳號可打所有對外端點，反之亦然。
- **使用者**：in-memory（`api`、`internal` 兩個**機器帳號**），帳密來自 `app.auth.*`（env 覆寫；見 `AppProperties.Auth`）。**不建 DB 使用者表**。
- **密碼刻意不做雜湊**：以 `{noop}` 前綴交給預設的 `DelegatingPasswordEncoder` 逐字比對。理由是雜湊在此對不上任何威脅模型（機器帳號、密碼本來就從 env 明文注入同一個 process，沒有會外洩的密碼資料庫），卻要付真實成本 —— STATELESS + Basic 表示**每個請求都重新認證**且無憑證快取，加上 `*Client` loopback 自呼叫的放大倍數，BCrypt（strength 10，單次數十毫秒）會直接吃掉 Resilience4j 併發/QPS 調校的預算。**真正的使用者密碼一律走外部 IdP。**
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
