# Swagger / OpenAPI 文件設計指南

> **最後更新**: 2026-06-09  
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

在 Java 側寫成 `@Schema(requiredMode = Schema.RequiredMode.REQUIRED)`（record component 上同樣有效）。
**這不只是文件好看的問題**：`required` 缺席時 `openapi-typescript` 之類的 codegen 會把每個欄位都產成
optional，呼叫端為了拿到堪用的型別只能另外手寫一份平行 interface —— 而手寫副本正是契約漂移能潛伏到
下游的原因（文件說謊時，手抄的那份不會紅）。

反過來也要誠實：只有**真的每次都出現**的欄位才標 required，否則就是拿文件騙 codegen。判準是「有沒有
測試釘住它一定出現」，不是「通常都有」。

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

**好的做法：** 用 **RFC 9457**（`application/problem+json`）—— 不必自己發明格式，而且 Spring 的
`ProblemDetail` 原生支援：

```json
{
  "type": "urn:problem:product-stock-not-enough",
  "title": "商品庫存不足",
  "status": 400,
  "detail": "商品 5 庫存不足（需要 10、剩 3）",
  "instance": "/product/5/stock",
  "code": "PRODUCT_STOCK_NOT_ENOUGH"
}
```

所有 API 都共用同一個格式。`code`（標準欄位之外唯一的 extension）是機器可讀識別碼，也是呼叫端唯一
該用來分辨錯誤種類的欄位 —— `status` 太粗（同一個 400 可能有好幾種原因）、`detail` 是給人看的且隨時
會改字。`title` 是這「類」問題的固定摘要，可直接顯示。驗證失敗會多帶一個 `errors` 陣列，逐筆給出
`field` / `message`，讓表單 UI 不必剖字串就能把訊息掛回對應的輸入欄位。

**不好的做法：** 每個 API 回傳不同結構
```json
// A API
{ "error": "not found" }
// B API
{ "message": "user not found" }
// C API
{ "code": 404 }
```

**也不好：** 自訂一套與 problem+json 平行的格式。只要 handler 繼承了 `ResponseEntityExceptionHandler`，
框架內建例外（405、415、malformed JSON…）本來就會產 `ProblemDetail` —— 自訂格式只會讓同一支 API 有
兩種錯誤形狀，而分岔點是「例外由誰攔到」這種呼叫端無法預測的內部細節。

**格式標準化只做了一半**：光是「所有 API 同一個形狀」不夠，那個形狀還得在 spec 裡**說清楚**，否則下游
只是換個地方手抄。六個欄位（`type`/`title`/`status`/`detail`/`instance`/`code`）標 `required`、`errors`
維持 optional（僅驗證失敗時出現）。至於 `code` 為什麼**不**列 enum，見下面原則 9。決策記錄見
`docs/api-response-contract-decision.md` Phase 7。

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

**也不好：** 用 `@Schema(allowableValues = {...})` 手抄一份值清單。那只是把「description 會漂移」換成
「allowableValues 會漂移」—— 清單要從 enum 本身推導出來：**讓欄位型別就是 Java enum**（springdoc 自動
產出 `enum` 與各常數名），沒辦法改型別時退而用 `@Schema(implementation = SomeEnum.class)`，它在
`String` 欄位上同樣有效。

**enum 的判準是「人看得完」**：`enum` 存在的意義是讓讀文件的人知道該填什麼、Swagger UI 能給下拉選單。
所以它適合**小而封閉、呼叫端要照著填**的值域 —— 訂單／商品／帳戶狀態這種。反過來，值域大到人不會逐項
閱讀時（本專案的 `ApiErrorResponse.code` 就有 50+ 個值），列 enum 的收益是零，成本卻是實的：spec 被撐
胖、每新增一個錯誤碼就多一次「文件說不允許」的假違約。**那種欄位把值域寫在 `description` 就好。**

> 💡 改完 enum 相關的 schema，dump 一次 `/v3/api-docs` 確認，不要憑 annotation 相信結果。用
> `generateOpenApiDocs` 驗證時**先確認 8787 沒有殘留的 app 行程** —— plugin 抓到舊 app 不會報錯，
> 只會靜默寫出舊 spec（徵兆：build 幾秒就結束，正常要 20~30 秒）。最保險是寫個 `@SpringBootTest`
> 用 MockMvc 打 `/v3/api-docs`。

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

但「不包信封」不等於「回裸純量」。建立資源回一個裸 `5` 同樣是壞契約 —— 線路上那個數字沒有名字，
呼叫端只能靠文件外的默契解讀，而且日後想多回一個欄位就是破壞性變更。回 `{"id": 5}` 不是信封，
而是**給值一個名字**；信封指的是 `success` / `data` 這種與業務無關、每個回應都重複一次的外層。
同理，`{"hasActiveOrder": true}` 優於裸 `true`。

至於「這次成功沒有內容可回」（更新、刪除）—— 那就回 `204 No Content`，不要回一個 `{}` 佔位，也不要
回 `{"success": true}`：status line 已經把這件事說完了。

---

### 🔑 大型 Spring Boot 專案三大核心原則

1. **Request/Response DTO 與 Entity 完全分離**
2. **Error Response 全系統統一**
3. **以業務領域物件建立可重用 Schema**（如 Address、Customer、Order、Money 等），而不是大量扁平欄位

這三項對 Swagger 的可維護性與 API 長期演進的影響最大。

---

## 專案現況評估

### 評估摘要（已更新 2026-06-09）

| 原則 | 符合度 | 說明 |
|------|--------|------|
| 1. Schema Object 封裝 | ✅ 良好 | `GetOrderDetailResponse` 使用 `List<OrderItemDTO>` 巢狀物件 |
| 2. 共用 Schema | ✅ 已改善 | DTO 已加上完整 `@Schema` 註解，Entity 已移除 `@Schema` |
| 3. 明確標示 required | ✅ 已改善 | DTO 使用 `requiredMode = Schema.RequiredMode.REQUIRED` 明確標示 |
| 4. 寫 description | ✅ 已改善 | 所有 DTO 欄位皆有 `description` |
| 5. 提供 example | ✅ 已改善 | 所有 DTO 欄位皆有 `example` |
| 6. Request/Response DTO 分離 | ✅ 優秀 | 完全分離：`CreateXxxRequest` / `UpdateXxxRequest` / `GetXxxResponse` |
| 7. Pagination 統一格式 | ✅ 已實作 | List 端點接受 `Pageable`（標 `@ParameterObject`）回傳 `PageResponse<T>`，預設 `page=0, size=20`；不提供非分頁列表 |
| 8. Error Response 標準化 | ✅ 優秀 | RFC 9457 `application/problem+json`（`ProblemDetail` + extension `code`，驗證失敗另帶 `errors`），由 `GlobalExceptionHandler` 單一組裝；`ApiErrorResponse` 僅為 schema 宣告 |
| 9. Enum 定義清楚 | ✅ 已改善 | DTO 中使用 `@Schema(description)` 說明可選值含義 |
| 10. Schema 不暴露 Entity | ✅ 優秀 | Entity 已移除所有 `@Schema`，僅 DTO 有 Swagger 註解 |
| 11. 善用巢狀物件 | ✅ 良好 | Order items 使用獨立 `OrderItemDTO` |
| 12. Response Wrapper | ✅ 優秀 | 沒有成功信封：建立回 `201` + `Location` + `{"id": n}`、無內容回 `204`、有內容才 `200` + 具名 DTO；契約由 `ApiSuccessContractTest` 釘住 |

---

### 詳細問題分析（已解決）

> ✅ 以下問題已於 2026-06-09 全部修正完成。

#### ~~問題 1：Entity 有 `@Schema` 但 DTO 沒有~~ ✅ 已解決

**解決方式：**
- 所有 DTO 已加上完整 `@Schema(description, example, requiredMode)` 註解
- Entity（`Account`, `Product`, `OrderInfo`, `OrderDetail`）已移除所有 `@Schema` 註解

---

#### ~~問題 2：Controller 缺少 `@ApiResponse` 註解~~ ✅ 已解決

**解決方式：** 所有 Controller 已加上 `@ApiResponses`，定義 200/400/404/409 等回應格式。

---

#### ~~問題 3：Controller 缺少 `@Tag` 註解~~ ✅ 已解決

**解決方式：** 所有 Controller 已加上 `@Tag(name, description)` 進行 API 分組。

---

#### ~~問題 4：PathVariable / RequestParam 缺少 `@Parameter` 描述~~ ✅ 已解決

**解決方式：** 所有 `@PathVariable` 已加上 `@Parameter(description, example, required)` 描述。

---

#### ~~問題 5：Enum 值未在 Swagger 中呈現~~ ✅ 已解決

**解決方式：** DTO 中使用 `@Schema(description)` 說明可選值含義，例如：
- `"帳戶狀態 (Y=啟用, N=停用)"`
- `"訂單狀態 (1001=訂單建立, 1003=訂單取消)"`

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
        @ApiResponse(responseCode = "201", description = "建立成功；Location 指向新資源，body 為 {\"id\": n}"),
        @ApiResponse(responseCode = "400", description = "參數驗證失敗",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CreatedResponse> createAccount(...) { ... }   // CreatedResponse.at(id)
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

### 改善 6：分頁格式統一（已實作）

已實作統一分頁回應格式，使用自訂 `PageResponse<T>` 包裝 Spring Data 的 `Page<T>`：

**實作位置：** `com.ibm.demo.util.PageResponse`

```java
@Schema(description = "分頁回應")
public record PageResponse<T>(
    @Schema(description = "資料內容") List<T> content,
    @Schema(description = "當前頁碼（從 0 開始）", example = "0") int page,
    @Schema(description = "每頁筆數", example = "20") int size,
    @Schema(description = "總筆數", example = "100") long totalElements,
    @Schema(description = "總頁數", example = "5") int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> springPage) { ... }
}
```

**已改造的 API：**

| Controller | 端點 | 原回傳 | 新回傳 |
|-----------|------|--------|--------|
| `AccountController` | `GET /account` | `List<GetAccountListResponse>` | `PageResponse<GetAccountListResponse>` |
| `ProductController` | `GET /product` | `List<GetProductListResponse>` | `PageResponse<GetProductListResponse>` |
| `OrderController` | `GET /order/account/{accountId}` | `List<GetOrderListResponse>` | `PageResponse<GetOrderListResponse>` |

**使用方式：**
- 預設每頁 20 筆
- 支援 `page`, `size`, `sort` 查詢參數
- 範例：`GET /account?page=0&size=10&sort=id,desc`

**Controller 端寫法（重要）：** `Pageable` 參數必須標 springdoc 的 `@ParameterObject`，**不可**用 `@Parameter`：

```java
public ResponseEntity<PageResponse<GetProductListResponse>> getProductList(
        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
```

`@Parameter` 是描述**單一** query 參數用的（如 `@PathVariable`、`@RequestParam`，見改善 4）。套在 `Pageable` 這種複合物件上，springdoc 會產出一個名為 `pageable`、schema 指向 `$ref: '#/components/schemas/Pageable'` 的 object 型參數 — Swagger UI 因此渲染成物件編輯器並要求 JSON，導致**無法直接送出請求**（且會被誤標為 `required: true`）。`@ParameterObject` 才會把物件攤平成 `page` / `size` / `sort` 三個獨立且 optional 的 query 參數，說明與預設值（`page=0`、`size=20`）取自 springdoc 內建的 `org.springdoc.core.converters.models.Pageable`。

**設計決策：**
- 選擇自訂 `PageResponse<T>` 而非直接回傳 `Page<T>`
- 原因：避免暴露 Spring 內部結構（`pageable`, `sort` 物件等），保持 API Contract 乾淨
- 保留原有非分頁方法供內部使用（如 `OrderClient` 的帳戶訂單檢查）

---

## 改善進度追蹤

| # | 優先度 | 項目 | 狀態 |
|---|--------|------|------|
| 1 | 🔴 高 | DTO 加上 `@Schema(description, example)` | ✅ 完成 |
| 2 | 🔴 高 | Controller 加上 `@ApiResponse` 定義錯誤回應 | ✅ 完成 |
| 3 | 🟡 中 | Controller 加上 `@Tag` 分組 | ✅ 完成 |
| 4 | 🟡 中 | PathVariable 加上 `@Parameter` | ✅ 完成 |
| 5 | 🟡 中 | Enum 值在 Schema 中明確標示 | ✅ 完成 |
| 6 | 🟢 低 | 移除 Entity 上的 `@Schema`（避免暴露內部結構） | ✅ 完成 |
| 7 | 🟢 低 | 分頁格式統一（`PageResponse<T>`） | ✅ 完成 |
