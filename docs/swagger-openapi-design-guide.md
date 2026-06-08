# Swagger / OpenAPI 文件設計指南

> **最後更新**: 2026-06-08  
> **適用專案**: SpringBoot Demo (Account / Order / Product)

---

## 目錄

1. [設計原則總覽](#設計原則總覽)
2. [專案現況評估](#專案現況評估)
3. [具體改善建議](#具體改善建議)

---

## 設計原則總覽

### 原則 1：使用 Schema Object 封裝相關欄位

**好的做法：**
```yaml
User:
  type: object
  properties:
    id:
      type: integer
    name:
      type: string
    email:
      type: string
Order:
  type: object
  properties:
    id:
      type: integer
    customer:
      $ref: '#/components/schemas/User'
```

**不好的做法：**
```yaml
Order:
  type: object
  properties:
    customerId:
      type: integer
    customerName:
      type: string
    customerEmail:
      type: string
```

**原因：**
- 避免欄位爆炸（Field Explosion）
- 提高重用性
- 未來新增欄位不影響外層結構

---

### 原則 2：共用 Schema，不要到處複製貼上

**好的做法：**
```yaml
components:
  schemas:
    User:
      ...

paths:
  /users:
    get:
      responses:
        200:
          schema:
            $ref: '#/components/schemas/User'
```

**不好的做法：** 每個 endpoint 都重新定義
```yaml
responses:
  200:
    schema:
      type: object
      properties:
        id:
          type: integer
        ...
```

**原因：**
- Single Source of Truth
- 修改一次全部同步

---

### 原則 3：明確標示 required 欄位

**好的做法：**
```yaml
User:
  type: object
  required:
    - name
    - email
```

**不好的做法：** 全部欄位都不寫 required。

使用者根本不知道：
- 哪些一定要傳
- 哪些可省略

---

### 原則 4：寫 description，不要讓欄位名稱自己說明自己

**好的做法：**
```yaml
email:
  type: string
  description: User login email address
```

**不好的做法：**
```yaml
email:
  type: string
```

對開發者來說，`email` 可能是：
- login email？
- contact email？
- notification email？

根本不知道。

---

### 原則 5：提供 example

**好的做法：**
```yaml
email:
  type: string
  example: bob@example.com
```

或整體範例：
```yaml
example:
  id: 1
  name: Bob
```

Swagger UI 可以直接顯示，對 API 使用者非常友善。

---

### 原則 6：Request DTO 與 Response DTO 分開

**常見錯誤：** 同一個 `UserDto` 同時用於 Create / Update / Query。

**建議：**
```yaml
CreateUserRequest:
  required:
    - name
    - email
UpdateUserRequest:
  required:
    - name
UserResponse:
  properties:
    id:
      type: integer
    name:
      type: string
```

- 建立時不需要 `id`
- 回傳時需要 `id`
- 更新時可能只需部分欄位

---

### 原則 7：Pagination 使用統一格式

**好的做法：**
```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

**不好的做法：** 每個 API 格式不一致
```json
// API A
{ "users": [], "count": 100 }
// API B
{ "items": [], "total": 100 }
// API C
{ "data": [], "records": 100 }
```

命名不一致會讓前端很痛苦。

---

### 原則 8：Error Response 標準化

**好的做法：**
```json
{
  "timestamp": "2026-06-08T12:00:00",
  "status": 400,
  "error": "ACCOUNT_001",
  "message": "帳戶尚未啟用"
}
```

所有 API 都共用同一個格式。

**不好的做法：** 每個 API 回傳不同結構
```json
// A API
{ "error": "not found" }
// B API
{ "message": "user not found" }
// C API
{ "code": 404 }
```

---

### 原則 9：Enum 要定義清楚

**好的做法：**
```yaml
status:
  type: string
  enum:
    - ACTIVE
    - INACTIVE
    - SUSPENDED
  description: |
    ACTIVE = user can login
    INACTIVE = user disabled
    SUSPENDED = admin suspended
```

**不好的做法：** 只寫 `type: string` 或 `type: integer`，使用者不知道可以傳什麼值。

---

### 原則 10：Schema 不要直接暴露 Entity

**常見問題：** 直接拿 JPA Entity 產 Swagger
```json
{
  "id": 1,
  "password": "xxx",
  "createdAt": "...",
  "version": 0,
  "deleted": false
}
```

**建議：** API Contract 獨立於 Database Schema
- `UserResponse` — 回傳用
- `CreateUserRequest` — 建立用
- `UpdateUserRequest` — 更新用

---

### 原則 11：善用巢狀物件，而不是過度扁平化

**推薦：**
```json
{
  "address": {
    "country": "TW",
    "city": "Tainan",
    "zipcode": "700"
  }
}
```

**不推薦：**
```json
{
  "addressCountry": "TW",
  "addressCity": "Tainan",
  "addressZipcode": "700"
}
```

尤其當 Address 本身是領域概念（Value Object）時，更應該獨立成 Schema。

---

### 原則 12：Response Wrapper 要有理由才使用

很多團隊習慣：
```json
{
  "success": true,
  "data": { ... }
}
```

但對 REST API 而言，`200 OK` 本身就代表成功。

如果沒有統一錯誤處理、traceId、metadata 等需求，可以直接：
```json
{
  "id": 1,
  "name": "Bob"
}
```

**不要為了包而包。**

---

### 🔑 大型 Spring Boot 專案三大核心原則

1. **Request/Response DTO 與 Entity 完全分離**
2. **Error Response 全系統統一**
3. **以業務領域物件建立可重用 Schema**（如 Address、Customer、Order、Money 等），而不是大量扁平欄位

這三項對 Swagger 的可維護性與 API 長期演進的影響最大。

---

## 專案現況評估

### 評估摘要

| 原則 | 符合度 | 說明 |
|------|--------|------|
| 1. Schema Object 封裝 | ✅ 良好 | `GetOrderDetailResponse` 使用 `List<OrderItemDTO>` 巢狀物件 |
| 2. 共用 Schema | ⚠️ 部分 | Entity 有 `@Schema`，但 DTO 完全沒有 |
| 3. 明確標示 required | ⚠️ 部分 | 有 `@NotBlank`/`@NotNull` validation，但 Swagger 層面未明確標示 |
| 4. 寫 description | ❌ 不足 | DTO 欄位完全沒有 description |
| 5. 提供 example | ❌ 不足 | DTO 欄位完全沒有 example |
| 6. Request/Response DTO 分離 | ✅ 優秀 | 完全分離：`CreateXxxRequest` / `UpdateXxxRequest` / `GetXxxResponse` |
| 7. Pagination 統一格式 | ⚠️ 未實作 | 目前 List API 直接回傳 `List<T>`，無分頁 |
| 8. Error Response 標準化 | ✅ 優秀 | `ApiErrorResponse` 統一格式 + `GlobalExceptionHandler` |
| 9. Enum 定義清楚 | ⚠️ 部分 | 有 enum class 但 DTO 中使用原始型別，Swagger 未顯示可選值 |
| 10. Schema 不暴露 Entity | ✅ 優秀 | Controller 回傳 DTO，不直接回傳 Entity |
| 11. 善用巢狀物件 | ✅ 良好 | Order items 使用獨立 `OrderItemDTO` |
| 12. Response Wrapper | ✅ 良好 | 沒有多餘包裝，直接回傳資料 |

---

### 詳細問題分析

#### 問題 1：Entity 有 `@Schema` 但 DTO 沒有

**現況：**
- `Account.java`, `Product.java`, `OrderInfo.java` 等 Entity 都有完整的 `@Schema(description, example)`
- 但實際 API 回傳的 DTO（如 `GetAccountDetailResponse`, `GetProductDetailResponse`）完全沒有任何 `@Schema` 註解

**影響：**
- Swagger UI 上 API 回傳的 Schema 沒有任何欄位說明
- Entity 的 `@Schema` 反而可能暴露內部結構（`AuditMetadata`, `SoftDeleteMetadata`, `version`）

---

#### 問題 2：Controller 缺少 `@ApiResponse` 註解

**現況：** Controller 只有 `@Operation(summary, description)`，沒有定義各種 HTTP 狀態碼回應。

**影響：** 使用者無法從 Swagger UI 得知：
- 400 Bad Request 的回應格式
- 404 Not Found 的回應格式
- 409 Conflict 的回應格式

---

#### 問題 3：Controller 缺少 `@Tag` 註解

**現況：** 沒有對 API 進行分組標記。

**影響：** Swagger UI 上所有 API 混在一起，不易瀏覽。

---

#### 問題 4：PathVariable / RequestParam 缺少 `@Parameter` 描述

**現況：** `@PathVariable Integer id` 沒有任何描述。

**影響：** 使用者不知道 `id` 代表什麼（帳戶 ID？訂單 ID？商品 ID？）

---

#### 問題 5：Enum 值未在 Swagger 中呈現

**現況：**
- `AccountStatus`: Y (啟用) / N (停用)
- `OrderStatus`: 1001 (訂單建立) / 1003 (訂單取消)
- `ProductStatus`: 1001 (可銷售) / 1002 (不可銷售)

但 DTO 中使用 `String status` 或 `Integer orderStatus`，Swagger 無法顯示可選值。

---

## 具體改善建議

### 改善 1：為 DTO 加上 `@Schema` 註解

**Account DTO 範例：**

```java
@Builder
@Schema(description = "建立帳戶請求")
public record CreateAccountRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "50 characters max")
        @Schema(description = "帳戶名稱", example = "Bobby", requiredMode = Schema.RequiredMode.REQUIRED)
        String name) {
}

@Builder
@Schema(description = "帳戶詳細資訊回應")
public record GetAccountDetailResponse(
        @Schema(description = "帳戶名稱", example = "Bobby")
        String name,

        @Schema(description = "啟用狀態", example = "Y", allowableValues = {"Y", "N"})
        String status) {
}
```

---

### 改善 2：Controller 加上 `@Tag` 和 `@ApiResponse`

```java
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
@Tag(name = "Account", description = "帳戶管理 API")
public class AccountController {

    @Operation(
        summary = "建立新帳戶",
        description = "建立新帳戶。成功則新增帳戶資料，預設狀態為啟用 'Y'。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "建立成功，回傳帳戶 ID"),
        @ApiResponse(responseCode = "400", description = "參數驗證失敗",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<Integer> createAccount(...) { ... }
}
```

---

### 改善 3：PathVariable 加上 `@Parameter`

```java
@GetMapping("/{id}")
public ResponseEntity<GetAccountDetailResponse> getAccountDetail(
        @Parameter(description = "帳戶 ID", example = "1", required = true)
        @PathVariable Integer id) {
    ...
}
```

---

### 改善 4：Enum 值在 Schema 中明確標示

**方案 A：使用 `allowableValues`**
```java
@Schema(description = "啟用狀態", example = "Y", allowableValues = {"Y", "N"})
String status
```

**方案 B：使用 description 說明**
```java
@Schema(description = "訂單狀態 (1001=訂單建立, 1003=訂單取消)", example = "1001")
Integer orderStatus
```

---

### 改善 5：考慮移除 Entity 上的 `@Schema`

Entity 上的 `@Schema` 會讓 Swagger 文件暴露內部結構。建議：
- 移除 Entity 上的 `@Schema` 註解
- 只在 DTO 上使用 `@Schema`
- 確保 Swagger 文件只反映 API Contract，不反映 Database Schema

---

### 改善 6：未來分頁建議

若未來需要分頁，建議統一使用 Spring Data 的 `Page<T>` 格式：

```java
@Schema(description = "分頁回應")
public record PageResponse<T>(
    @Schema(description = "資料內容") List<T> content,
    @Schema(description = "當前頁碼", example = "0") int page,
    @Schema(description = "每頁筆數", example = "20") int size,
    @Schema(description = "總筆數", example = "100") long totalElements,
    @Schema(description = "總頁數", example = "5") int totalPages
) {}
```

---

## 優先改善順序

1. **🔴 高優先** — DTO 加上 `@Schema(description, example)`
2. **🔴 高優先** — Controller 加上 `@ApiResponse` 定義錯誤回應
3. **🟡 中優先** — Controller 加上 `@Tag` 分組
4. **🟡 中優先** — PathVariable 加上 `@Parameter`
5. **🟡 中優先** — Enum 值在 Schema 中明確標示
6. **🟢 低優先** — 移除 Entity 上的 `@Schema`（避免暴露內部結構）
7. **🟢 低優先** — 未來分頁格式統一規劃
