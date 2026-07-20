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

- [ ] 繼承 `util/BaseEntity`（`@MappedSuperclass`，含 `id`、audit 欄位、`@Version`）
  > ⚠️ `BaseEntity` 已標記 `@Deprecated(forRemoval=true)`，建立前先確認是否仍適用。
- [ ] 標註 `@Entity`、`@Table(name = "...")`
- [ ] 使用 `@SuperBuilder`（配合 `BaseEntity`）
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
- [ ] 失敗情境：拋出 `exception/` 下的 `BusinessException` 子類別，**不要直接回傳 error response**
- [ ] 需要呼叫其他 domain？→ 注入對應 `*Client`（`AccountClient`、`ProductClient`、`OrderClient`），**不要直接注入其他 domain 的 Service**
- [ ] 需要 Resilience4j？→ 在方法上加 `@Bulkhead`、`@CircuitBreaker`、`@RateLimiter`（name 需與 `application.yml` 中的 key 對應）

### 4. Controller

- [ ] 標註 `@RestController`、`@RequestMapping("/api/{domains}")`、`@RequiredArgsConstructor`
- [ ] 參數驗證用 `@Valid`
- [ ] 回傳 `ResponseEntity<T>`，明確控制 HTTP status
- [ ] 列表端點接受 `Pageable` 參數，回傳 `ResponseEntity<PageResponse<{Domain}Response>>`
  - 預設 `page=0, size=20`
  - **不提供非分頁的列表端點**
- [ ] 加上 Swagger 註解（`@Operation`、`@ApiResponse`、`@Tag`）

### 5. DTO

- [ ] `{Domain}Request`：加 Bean Validation 註解（`@NotNull`、`@NotBlank` 等）
- [ ] `{Domain}Response`：僅包含外部需要的欄位，避免暴露 entity 內部欄位
- [ ] 使用 `record` 或加 Lombok `@Data`/`@Value`

### 6. 錯誤處理

- [ ] 在 `exception/` 下新增該 domain 的 `BusinessException` 子類別
  - 例：`{Domain}NotFoundException`、`{Domain}AlreadyExistsException`
- [ ] 在 `util/ErrorCode` 加入對應的錯誤碼（若有需要）
- [ ] `GlobalExceptionHandler` 已處理 `BusinessException` 父類別，通常不需要額外改動

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
| 分頁 | 所有列表端點用 `Pageable` + `PageResponse<T>`，無例外 |
| 錯誤 | 拋 `BusinessException` 子類別，由 `GlobalExceptionHandler` 統一處理 |
| 安全 | `*Client` 自呼叫需帶 `internal` 帳號憑證 |
