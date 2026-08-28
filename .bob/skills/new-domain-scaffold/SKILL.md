---
name: new-domain-scaffold
description: >
  在本專案新增一個完整 domain（如 account/product/order）的建立清單。
  確保每個 domain 都遵循統一的 feature-sliced 架構、分頁慣例、錯誤處理、
  soft delete 與 auditing 規範，不靠記憶力就能做到一致。
  觸發時機：使用者要新增一個新的 domain / module。
---

# New Domain Scaffold

新增一個 domain `{domain}` 時，依序完成以下清單。base package：`com.ibm.demo`。

## 目錄結構

```
src/main/java/com/ibm/demo/{domain}/
├── {Domain}Controller.java
├── {Domain}Service.java
├── {Domain}Repository.java
├── {Domain}Entity.java         (或 {Domain}.java)
└── dto/
    ├── {Domain}Request.java
    └── {Domain}Response.java
```

---

## Checklist

### 1. Entity

- [ ] **不要繼承任何基底類別**（舊的 `util/BaseEntity` `@MappedSuperclass` 已移除，一律用組合）
- [ ] 標註 `@Entity`、`@Table(name = "...")`
- [ ] 稽核欄位：以 `@Embedded` 組合 `util/AuditMetadata`；需要 soft delete 再以 `@Embedded` 組合 `util/SoftDeleteMetadata`
- [ ] `@Version` optimistic locking 欄位**直接定義在 entity**（因 JPA 不支援放進 `@Embeddable`）
- [ ] 使用 `@Builder`（不需 `@SuperBuilder`，已無繼承基底）
- [ ] 關聯欄位標註 `@ToString.Exclude` 避免循環引用
- [ ] 需要 soft delete？→ 加 `@SQLRestriction("deleted = 0")`（`SoftDeleteRepository` 要求）

### 2. Repository

- [ ] 繼承 `JpaRepository<{Domain}Entity, Long>`
- [ ] 需要 soft delete？→ 改繼承 `util/SoftDeleteRepository`
  - 勿自行加 `deleted = false` 過濾，`@SQLRestriction` 已處理
- [ ] 優先用方法名衍生查詢（`findByXxx`）
- [ ] 複雜查詢用 `@Query` JPQL（優先於 native SQL）

### 3. Service

- [ ] 標註 `@Service`、`@RequiredArgsConstructor`
- [ ] 查詢方法加 `@Transactional(readOnly = true)`
- [ ] 寫入方法加 `@Transactional`
- [ ] 失敗情境：拋出 `BusinessException` 並帶入對應 `ErrorCode`（`new BusinessException(ErrorCode.X, "...")`），**不要直接回傳 error response**
- [ ] 需要呼叫其他 domain？→ 注入對應 `*Client`（`AccountClient`、`ProductClient`、`OrderClient`），**不要直接注入其他 domain 的 Service**
- [ ] 需要 Resilience4j？→ 在方法上加 `@Bulkhead`、`@CircuitBreaker`、`@RateLimiter`（name 需與 `application.yml` 中的 key 對應）

### 4. Controller

- [ ] 標註 `@RestController`、`@RequestMapping("/api/{domains}")`、`@RequiredArgsConstructor`
- [ ] 參數驗證用 `@Valid`
- [ ] 回傳 `ResponseEntity<T>`，明確控制 HTTP status，且**不加成功信封**：
  - 建立資源 → `util/CreatedResponse.at(id)`（`201` + `Location` + `{"id": n}`），不要回裸 `Integer`
  - 成功但沒有內容可回（更新／刪除／內部操作）→ `ResponseEntity.noContent().build()`（`204`，body 必須為空）
  - 有內容才 `200` + 具名 DTO 或 `PageResponse<T>`；**不回裸純量**（線路上的 `true`／`5` 沒有名字，呼叫端還會踩到隱含的 auto-unboxing NPE，且無法相容擴充）
  - 新端點請一併加進 `src/test/java/com/ibm/demo/contract/ApiSuccessContractTest.java`
- [ ] 列表端點接受 `Pageable` 參數，回傳 `ResponseEntity<PageResponse<{Domain}Response>>`
  - 預設 `page=0, size=20`
  - **不提供非分頁的列表端點**
  - `Pageable` 必須標 `@ParameterObject`（`import org.springdoc.core.annotations.ParameterObject`），**不可**用 `@Parameter` — 後者會讓 Swagger UI 渲染成單一 object 參數而無法送出請求（詳見 `docs/swagger-openapi-design-guide.md` 改善 6）
- [ ] 加上 Swagger 註解（`@Operation`、`@ApiResponse`、`@Tag`）

### 5. DTO

- [ ] `{Domain}Request`：加 Bean Validation 註解（`@NotNull`、`@NotBlank` 等）
- [ ] `{Domain}Response`：僅包含外部需要的欄位，避免暴露 entity 內部欄位
- [ ] 使用 `record` 或加 Lombok `@Data`/`@Value`

### 6. 錯誤處理

- [ ] 在 `exception/ErrorCode` 加入該 domain 需要的錯誤碼（`HttpStatus` + `title` 兩個參數）；**不要**另外帶 `SYS_001` 風格的編號 —— `getCode()` 就是常數名，`type` 由它機械推導
- [ ] throw 時用 `new BusinessException(ErrorCode.X, "...")`；**不需要**新增例外子類別（已整併為單一具體 `BusinessException`）
- [ ] 系統／整合失敗（下游壞了，不是使用者的錯）拋 `SystemException("...")`，排查資訊用 `.with(key, value)` 掛 context，不要串進 message
- [ ] `GlobalExceptionHandler` 已統一處理這兩個型別，通常不需要額外改動；**不要在 Service 自己 log 例外**
- [ ] 對外錯誤格式是 RFC 9457 `application/problem+json`（`type`/`title`/`status`/`detail`/`instance` + extension `code`，驗證失敗另帶 `errors`），**不要自己組 error body**；Swagger 上的 error response 用 `@Schema(implementation = ApiErrorResponse.class)` 引用（它只是 schema 宣告，不參與執行期序列化）

### 7. DB Migration

- [ ] 在 `src/main/resources/db/migration/` 新增 Flyway script
  - 命名格式：`V{版號}__{描述}.sql`（雙底線）
  - 新版號需大於現有最大版號
- [ ] Oracle 語法：注意大小寫、`NUMBER` 型別、`SEQUENCE` 等 Oracle 特有用法
- [ ] 若有 soft delete 欄位，在此建立（通常是 `DELETED NUMBER(1) DEFAULT 0`）

### 8. 跨模組整合（若需要）

- [ ] 是否需要新建一個 `{Domain}Client`（讓其他 domain 呼叫本 domain）？
  - 在 `{domain}/` 下建立 `{Domain}Client.java`，底層用 `RestClient`
  - 在 `config/RestClientConfig.java` 註冊 bean
  - **附上 `internal` 帳號 Basic 憑證**（因為呼叫會繞回本應用，需通過 SecurityFilterChain）

### 9. 測試

- [ ] 單元測試：`{Domain}ServiceTest`，繼承 `@ExtendWith(MockitoExtension.class)`，遵循 AAA
- [ ] 整合測試：`{Domain}IntegrationTest`，**繼承 `BaseIntegrationTest`**
  - 勿使用 `@Testcontainers`/`@Container`（見 integration-test-runner skill）

---

## 架構原則提醒

| 規則 | 說明 |
|------|------|
| 跨 domain 呼叫 | 一律經由 `*Client`，不直接注入其他 domain 的 Service |
| Soft delete | 繼承 `SoftDeleteRepository`，不手刻 `deleted = false` 條件 |
| 分頁 | 所有列表端點用 `Pageable` + `PageResponse<T>`，無例外；`Pageable` 一律標 `@ParameterObject`，不可用 `@Parameter` |
| 錯誤 | 拋 `BusinessException` 並帶入 `ErrorCode`，由 `GlobalExceptionHandler` 統一處理 |
| 安全 | `*Client` 自呼叫需帶 `internal` 帳號憑證 |
