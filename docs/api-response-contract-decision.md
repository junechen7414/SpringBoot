# API 回應契約決策（ADR）

> **狀態**：已採用（2026-08-27）· **範圍**：對外 HTTP 回應格式（成功與失敗）
> **進度**：Phase 0 ~ 5 ✅ 全部完成（錯誤側 RFC 9457、成功側 HTTP 原生語意），文件同步亦已完成（見 §8）。
> §5「現況稽核」是**2026-08-27 當時**的盤點，刻意保留原文作為變更前的存證 —— 讀它請對照 §7 各 phase
> 的完成註記，不要當成今天的現況。

---

## 1. 問題陳述

起因是一個設計上的困惑：

> RFC 9457（Problem Details for HTTP APIs）只規範**失敗**回應，所以它無法消除「成功回應」與「失敗回應」形狀不同的問題。
> 若改成自己組統一信封（envelope），成功與失敗形狀一致了，卻變成自訂契約、失去框架與工具鏈的現成支援。
> 有沒有一個 general best practice 能解決這個張力？

答案是有，但它不是「讓成功與失敗形狀一樣」。而在盤點本專案後更發現：**真正的問題不是那個張力，而是現況比想像中更不一致**（§5）。

---

## 2. 一致性的兩個軸

「回應不一致」這句話混了兩件不同的事，必須先拆開：

### 軸一：成功 vs 失敗形狀不同 —— **不是缺陷**

在 HTTP 語意下，這是刻意設計。兩者回答的是**不同的問題**，理應有不同的 schema，連 media type 都該不同：

- 成功 → `application/json`，body 是「你要的那個資源」
- 失敗 → `application/problem+json`，body 是「為什麼不行」

客戶端的判別子是 **status code**（次要是 `Content-Type`），不是 body 裡的某個欄位。前端寫「兩套解析邏輯」不是技術債，而是正確地反映了兩種語意——而且其中一套（失敗）可以是**函式庫幫你寫好的**，只要格式是標準的。

### 軸二：同類回應之間形狀不同 —— **這才是真正的不一致**

失敗回應彼此形狀不同、成功回應彼此形狀不同。這是本專案實際存在的問題（§5），而且它**不會**因為導入信封而消失——信封只是換一個地方不一致。

> 把力氣花在軸一（讓成功長得像失敗）而不是軸二，是這類討論最常見的誤判。

### HTTP 本身就是那個信封

自訂信封之所以被 Zalando RESTful Guidelines、Microsoft REST API Guidelines、Google AIP 一致視為 anti-pattern，原因是它把 status code 已經表達的東西複製進 body。而一旦 body 有了 `success` 欄位，就必然滑坡到「`200 OK` + `success: false`」——那會同時破壞：

- **HTTP 快取**（錯誤被當成可快取的成功表述）
- **重試語意**（客戶端與 SDK 依 status 決定是否重試）
- **中介層**（proxy、gateway、service mesh 只看 status）
- **監控告警**（error rate 指標永遠是 0%，包括本專案的 Grafana 面板）
- **codegen**（每個端點的回傳型別變成 `Envelope<T>`，客戶端要雙層 unwrap）

---

## 3. 三種立場與取捨

只有三種**內部自洽**的立場，必須選一個並貫徹到底。混用是最糟的結果，而那正是現況。

| | 成功回應 | 失敗回應 | 代價 |
|---|---|---|---|
| **A. HTTP-native**（採用） | 裸資源表述 + `Location` / `ETag`；集合用分頁包裝 | RFC 9457 `application/problem+json` | 成功與失敗形狀不同，需接受這是刻意設計而非缺陷 |
| B. 全域信封（JSON:API 或自訂 `{data}` / `{errors}`） | `{data, meta}` | `{errors: [...]}` | 需自行攔截**所有**出口，否則必漏；codegen 退化；本 repo 的具體爆點見下 |
| C. RPC over HTTP（一律 `200`） | body 帶狀態碼 | 同左 | 只適合單一緊耦合前端；放棄整條 HTTP 中介層的語意 |

### 為什麼不選 B —— 本 repo 的具體爆點

不是抽象上的偏好問題，而是這個 repo 導入信封會直接踩到：

1. **三個 `*Client` 全部裸型別反序列化。** `AccountClient` / `ProductClient` / `OrderClient` 是 Boot 4 `@HttpExchange` 介面，回傳型別**就是** controller 的 body 型別，沒有任何 unwrap 層。包一層後全部要改。
2. **`AccountService` 的刪除前檢查依賴精確 wire shape。** 現行程式呼叫 `orderClient.getOrderExistence(accountId).hasActiveOrder()`，期待 body 直接反序列化為 `OrderExistenceResponse`；若無同步修改 client 回傳型別就擅自再包成功信封，該欄位無法按既有契約取得，而這條路徑正是「刪除帳戶前檢查是否仍有訂單」。
3. **`OpenApiConfig.java` 沒有掛點。** 只宣告 security scheme，沒有 `OpenAPI` bean、沒有 `GroupedOpenApi`、沒有 `OpenApiCustomizer`，要讓 springdoc 正確描述 `Envelope<T>` 得從零建。
4. **兩個 testdata controller 回 plain `String`。** `JMeterTestDataController` / `PlaywrightTestDataController` 的 body 是人類可讀字串，而下游 Playwright E2E 直接依賴 `/PlaywrightTestData/*`。
5. **需要改寫既有設計文件。** `docs/swagger-openapi-design-guide.md` 原則 12（第 307–327 行）明文寫「不要為了包而包」，自評表（第 358 行）把「沒有多餘包裝」列為 ✅ 良好。選 B 等於推翻既有決策。

### `PageResponse<T>` 不算違反立場 A

分頁 metadata（`page` / `size` / `totalElements` / `totalPages`）本來就**不屬於資源本身**，集合包裝是公認做法（Spring Data `Page`、JSON:API `meta`、Google AIP `nextPageToken`）。它包的是「這一頁」而不是「這次呼叫」，與信封的差別在此。`util/PageResponse.java` 維持不動。

---

## 4. 決策：立場 A（HTTP-native + RFC 9457）

**成功**：裸 DTO 或 `PageResponse<T>`，無信封。
**失敗**：一律 RFC 9457 `application/problem+json`。

兩個關鍵事實讓這個選擇的成本遠低於直覺：

### RFC 9457 不是外來標準，它已經裝了一半

`GlobalExceptionHandler extends ResponseEntityExceptionHandler`。在 Spring Framework 6+ 上，**這個基底類別把它處理的所有內建 MVC 例外渲染成 `ProblemDetail` + `application/problem+json`**——而本專案只覆寫了 `handleMethodArgumentNotValid` 一個方法。也就是說 RFC 9457 今天就在 wire 上（§5.1 格式 #2），反而是自訂的 `ApiErrorResponse` 才是那個少數派。

> 注意：`spring.mvc.problemdetails.enabled` 這個設定在本專案是 no-op。它只控制「**沒有**繼承 `ResponseEntityExceptionHandler` 的應用」所使用的獨立 `ProblemDetailsExceptionHandler` advice。本專案已經繼承了基底類別，行為與該旗標無關。

### 成功側無信封是延續既有決策，不是新政策

見 §3 第 5 點。立場 A 讓文件與程式碼一致；立場 B 需要同時改寫 `docs/swagger-openapi-design-guide.md` 原則 12。

---

## 5. 現況稽核（2026-08-27 盤點，保留原文）

> 以下是**變更前**的狀態。Phase 1 消滅了格式 #3、Phase 2 消滅了格式 #1、Phase 4 補齊了 validation
> 的資訊遺漏，因此 §5.1 的三種格式今天只剩一種（RFC 9457）。保留原文的理由是：這些條目正是各 phase
> 的驗收標準，刪掉就無從檢查「當初列的問題是否真的都收掉了」。

### 5.1 錯誤側：三種 wire format 並存

`docs/handout/03-exception-handling.md:3` 宣稱的「回應格式永遠一致，前端只需寫一套解析邏輯」**今天並不成立**。

| # | 格式 | 誰產生 | 涵蓋範圍 |
|---|---|---|---|
| 1 | `ApiErrorResponse`<br>`timestamp` / `status` / `code` / `error` / `message` | `GlobalExceptionHandler` 的 8 個 handler | `BusinessException`、resilience4j 三種拒絕（bulkhead / circuit / rate limit）、樂觀鎖衝突、`SystemException`、`Exception` 兜底、`handleMethodArgumentNotValid` override |
| 2 | **RFC 9457 `ProblemDetail`**<br>`type` / `title` / `status` / `detail` / `instance` | 繼承來的 `ResponseEntityExceptionHandler`（**未覆寫的部分**） | malformed JSON（`HttpMessageNotReadableException`）、405、415、406、缺 `@RequestParam`、型別轉換失敗、`HandlerMethodValidationException`、未知 URL 的 404 |
| 3 | `DefaultErrorAttributes`<br>`timestamp`（ISO-8601）/ `status` / `error` / `path`<br>**無 `code`、多 `path`** | Security filter chain `sendError` → forward `/error` → `BasicErrorController` | 401、403 |

格式 #3 的成因是**架構性的**：`SecurityConfig.java:64-71` 沒有設 `exceptionHandling { authenticationEntryPoint / accessDeniedHandler }`，而 `@RestControllerAdvice` 活在 `DispatcherServlet` **內部**、filter chain **之後**。Security 的拒絕發生在 filter 層，advice 永遠攔不到——這不是漏寫一個 handler 就能補的，必須從 `SecurityConfig` 下手。

### 5.2 連帶問題

- **`code` 命名空間分裂。** `ErrorCode` 風格（`SYS_001`、`PRODUCT_003`、`ORDER_001`、`ACCOUNT_002`）與 handler 字面值（`RATE_LIMITED`、`VALIDATION`、`INTERNAL_ERROR`、`OPTIMISTIC_LOCK`、`BULKHEAD_FULL`、`CIRCUIT_OPEN`）共用同一個欄位。`docs/handout/03-exception-handling.md` 的已知不一致段落已記「留待整體錯誤契約調整時一併處理」——**本 ADR 就是那個時機**。
- **`@PreAuthorize` 定時炸彈。** 目前全專案無方法級 authZ（`SecurityConfig.java:109` 自己註明 `roles` 沒有任何授權規則在用）。一旦導入，`AuthorizationDeniedException` 會被 `handleUnexpected(Exception.class)` 吃掉變成 **500 而非 403**。`GlobalExceptionHandler.java:104-107` 已自行標註此陷阱。
- **validation 資訊遺漏。** `handleMethodArgumentNotValid`（`GlobalExceptionHandler.java:120-141`）只讀 `getBindingResult().getFieldErrors()`，**`getGlobalErrors()`（class-level 跨欄位驗證）被靜默丟棄**；且欄位錯誤被壓成單一字串 `參數驗證失敗: [a: ...]; [b: ...]`，機器不可讀。`@RequestParam` / `@PathVariable` 的驗證（`HandlerMethodValidationException`）未覆寫，落到格式 #2。
- **`RestClientErrorHandler` 綁死 `ApiErrorResponse`。** `RestClientErrorHandler.java:46` 用自行注入的 `ObjectMapper.readValue(body, ApiErrorResponse.class)`；`catch (Exception e)`（第 51–53 行）是**空的、無 log**，解析失敗會靜默退化成 `response.getStatusText()`。因為 app 走 loopback 自呼叫，改格式若無配套，跨 domain 錯誤訊息會無聲降級。細節與正解見 §7 Phase 3。
- **`timestamp` 無時區。** `GlobalExceptionHandler.java:178` 用 `LocalDateTime.now()`，序列化為 `yyyy-MM-dd HH:mm:ss`，無 offset。
- **沒有 `traceId`。** `docs/swagger-openapi-design-guide.md:319` 只是假設性提及；專案未導入 Micrometer Tracing。

### 5.3 成功側：六種形狀

`PageResponse<T>`（3 處）、單一 DTO（3）、裸 `Integer`（3 個建立端點）、裸 `Boolean`（1）、plain `String`（2 個 testdata controller）、空 body（9，其中 3 個是裸 `void` 而非 `ResponseEntity<Void>`）。

具體待整理項（**屬「成功側衛生」，非 RFC 9457 範圍，優先度低於錯誤側**）：

- 全 repo 只有 `DELETE /order/{orderId}` 回 **204**；`DELETE /account/{id}`、`DELETE /product/{id}` 回 200 空 body。
- 三個建立端點（`POST /account`、`POST /product`、`POST /order`）回 **200 + 裸 `Integer`**，無 201、無 `Location`。
- `GET /order/account/{accountId}/exists` 回裸 `true` / `false`（見 §3 第 2 點的 NPE 風險）。
- `PUT /order` 沒有 path variable（id 在 body 裡）。
- `GET /product/batch` 回裸 array；同一份資料在 `ProductService`（`Map`）→ controller（`List`）→ `OrderService`（`Map`）被轉三次形狀。
- `POST /product/reserve|release|adjustStock` 三個內部端點回裸 `void`，是唯一不包 `ResponseEntity` 的 handler。

### 5.4 安全網缺口（**最高風險**）

- `src/test` 內 `jsonPath` / `MockMvc` / `WebTestClient` / `TestRestTemplate` / `RestAssured` **全部 0 hit**。連 `GlobalExceptionHandlerTest` 都是 `new GlobalExceptionHandler()` 直接斷言 record accessor，**沒有經過 Jackson**。
- CI gate 是 `./gradlew test -Djunit.platform.exclude.tags=SanityTest`，**任何 wire format 改動都不會被攔下**。連 `ApiErrorResponse` 的 `@JsonFormat` timestamp pattern 與 record 欄位名都沒有任何測試覆蓋。
- 第一個發現破壞的地方會是**下游 Playwright E2E**，而且是在 image 已經推出去之後。這個劇本發生過一次——`OrderReadEndpointOsivIntegrationTest` 的 javadoc 明載它是「OSIV 關閉導致 order 端點 500，未被單元／整合測試攔下、僅由下游 E2E 抓到」的補課。
- 下游的 TypeScript 型別是從**被測容器的 live `/v3/api-docs`** 產生，不是讀版控的 `docs/swagger.json`（後者只是可讀記錄）。
- **型別產生不是唯一的下游耦合點**：`services/apis/base-api-client.ts` 的 `expectError()` 在**執行期**硬性要求 error body 具備 `message` / `error` / `status` 三個欄位，另有 5 處直接字面比對 `errorBody.message`（`account.spec.ts` 2 處、`order.spec.ts` 3 處）。也就是說**改欄位名會讓下游測試在執行期失敗，而不是在型別產生階段**。相對地 `expectOk()` 只斷言 `200 <= status < 300`，成功側的狀態碼調整它完全無感。

---

## 6. 目標契約規格

### 失敗：一律 `application/problem+json`

`ProblemDetail` 標準成員 + extension members：

| 成員 | 來源 | 說明 |
|---|---|---|
| `type` | 由 `code` 推導的穩定 URI | **採用 `urn:problem:<kebab-code>`**（例：`urn:problem:product-stock-not-enough`）。不用 http(s) URI，因為本專案不打算真的架一個可解析的錯誤說明頁；`about:blank` 只用於沒有特定型別者。`type` 是 RFC 9457 的主要判別子，**一旦公開就不得變更** |
| `title` | `ErrorCode.getMessage()` 或 handler 字面 error type | 對應現在的 `error`。同一個 `type` 的 `title` 應保持穩定 |
| `status` | 同現況 | 與 HTTP status line 一致 |
| `detail` | throw 點的 `ex.getMessage()` | 對應現在的 `message`。**500 維持不透明固定文案**（`GlobalExceptionHandler.internalServerError()` 現有的安全考量不變） |
| `instance` | request URI | 順便補上現在缺的 `path`（格式 #3 有、#1 沒有） |
| `code`（extension） | 現有 `code`，**收斂為單一命名空間** | RFC 9457 沒有對應的標準成員，故為 extension。`type` 給機器分流、`code` 給人類 grep log，兩者一對一。收斂方向：**全部進 `ErrorCode` enum**，讓 `RATE_LIMITED` 等字面值也有正式的代碼與 `HttpStatus`——但這會讓 `ErrorCode` 出現 5xx 常數，**因此必須同時檢查 `BusinessException` 的 `writableStackTrace=false` 前提**（其 javadoc 明載此前提只在「所有 `ErrorCode` 皆為 4xx」時成立） |
| `errors`（extension，僅 validation） | `List<{field, message}>` | 取代現在壓平的字串，並**補上 `getGlobalErrors()`** |
| ~~`timestamp`~~ | —— | **決定移除**。HTTP 已有 `Date` response header，多數 RFC 9457 實作不放 timestamp；保留它只是把現在 `LocalDateTime` 無時區的缺陷帶進新契約。若日後有稽核需求，改為 `Instant`（ISO-8601 + `Z`）再加回 |

`traceId` 列為後續選項，需先導入 Micrometer Tracing，不在本 ADR 範圍。

### 成功：維持無信封

沒有全域信封（沒有 `success` / `data` 外層），狀態碼由 HTTP 語意決定，三條規則涵蓋所有端點：

| 情形 | 回應 | 說明 |
|---|---|---|
| 建立資源 | **201** + `Location` + `{"id": n}` | `Location` 指向新資源的 URI，body 只帶 `id`。統一由 `util/CreatedResponse.at(id)` 組出 —— 「201 該長什麼樣」只有一個實作，不會逐個 controller 各寫一次 |
| 成功但沒有內容可回 | **204**，body 為空 | 更新、刪除、內部庫存變動、資格驗證。「沒東西可回」與「回一個空殼」是不同的兩件事，前者用狀態碼表達即可 |
| 成功且有內容 | **200** + 裸 DTO 或 `PageResponse<T>` | 一律具名物件，**不回裸純量**（裸 `5` / 裸 `true` 在線路上沒有說自己是什麼，且無法相容擴充） |

例外（刻意保留，見 §8）：`GET /product/batch` 回裸 array；`PageResponse<T>` 不動；testdata controller 不視為產品 API。

---

## 7. 分階段落地路徑

> 順序刻意如此：**先補安全網，再收架構性破口，最後才換格式**。跳過 Phase 0 等於盲改。

### Phase 0 —— 安全網（前置條件，不可跳過）

新增 `@WebMvcTest` / `MockMvc` 層測試，把**現行**三種錯誤格式與代表性成功回應的 JSON 釘住（`jsonPath` 斷言欄位名、timestamp pattern、`Content-Type`）。

理由見 §5.4：目前零序列化覆蓋。這層測試是後續每個 phase 的迴歸基準——**每個 phase 都應該讓其中一部分斷言刻意變紅，紅的範圍就是該 phase 的契約變更範圍**。

**已完成**（`src/test/java/com/ibm/demo/contract/`，17 個案例）。落地時實測到的三件事，記下來免得重踩：

- **安全網有效性已用突變測試證明**：暫時給 `ApiErrorResponse.message` 加上 `@JsonProperty("detail")`（正是 Phase 2 會做的改名），17 個案例中 8 個變紅；同一個突變下 `GlobalExceptionHandlerTest`（型別層斷言）**全綠** —— 這正是本 phase 要補的縫。
- **Boot 4 把 MVC test slice 拆成獨立模組**：`@WebMvcTest` 不再位於 `spring-boot-test-autoconfigure`，套件改為 `org.springframework.boot.webmvc.test.autoconfigure`，且 `spring-boot-starter-test` **不會**帶進來，須自行加 `spring-boot-starter-webmvc-test`。
- **`@WithMockUser` 不可掛在最外層類別**：`@Nested` 會繼承外層的測試 SecurityContext，「未帶憑證應回 401」的案例會因此拿到 200。認證要逐一掛在需要的方法／nested class 上。另外 `spring-security-test` 單獨存在還不夠 —— 把測試 SecurityContext 接進 MockMvc filter chain 的那段自動設定在 `spring-boot-starter-security-test`，少了它 `@WithMockUser` 全部被當成未認證（一次 401 洗掉 16 個案例）。

### Phase 1 —— 消滅格式 #3（Security）✅ 已完成

- `SecurityConfig` 補 `exceptionHandling { authenticationEntryPoint / accessDeniedHandler }`，把 401 / 403 導回應用層的統一格式（401 需保留 `WWW-Authenticate` header）。
- 同時補 `AuthorizationDeniedException` 的明確 handler，拆掉 §5.2 的 `@PreAuthorize` 定時炸彈——**在 `handleUnexpected` 之前**。
- 注意 `openApiFilterChain`（`SecurityConfig.java:79-86`，`@Profile("openapi")` 全放行）不受影響。

**已完成的實作與過程中確認的事實：**

- **本 phase 收斂到「現行的 `ApiErrorResponse`」，不是直接跳到 `problem+json`。** 原計畫寫的是後者，但那會製造一個中間狀態：401/403 已是 problem+json、其餘 8 個應用層錯誤還是 `ApiErrorResponse`——格式數量沒減少，只是換了一組不一致的組合。改成先讓 Security 走同一個組裝漏斗，Phase 2 再讓漏斗整體翻成 `ProblemDetail`，就只需要改一個地方。
- **委派機制**：Security filter chain 跑在 DispatcherServlet 之前，`@RestControllerAdvice` 永遠接不到 401/403。作法是 entry point / denied handler 注入 `@Qualifier("handlerExceptionResolver")` 的 `HandlerExceptionResolver`，以 `handler = null` 呼叫 `resolveException(...)`——advice 不綁定特定 handler method，可正常解析。解析回傳 `null` 時 `sendError(fallbackStatus)` 兜底：這不是裝飾用的，少了它「認證失敗」會變成 200。
- **entry point 要掛兩處**：`httpBasic(basic -> basic.authenticationEntryPoint(...))` 管「帶了憑證但錯」，`exceptionHandling(...)` 管「完全沒帶憑證」。只設一邊，另一半會掉回 servlet 容器的錯誤頁（也就是格式 #3 沒被消滅）。
- **`WWW-Authenticate` 必須自己補**：繞過 `BasicAuthenticationEntryPoint` 之後沒人會設它，漏掉就等於把 Basic 的認證挑戰從對外契約上拿掉。
- **401 / 403 的 `message` 固定為常數**，不帶 `ex.getMessage()`：Spring Security 的訊息會區分「找不到使用者」與「密碼錯誤」，回給呼叫端等於送出帳號枚舉的線索。要排查看 log 就好。
- **契約測試的紅區恰好等於本次變更範圍**：Phase 0 的 17 條斷言只有 `unauthenticated`（401 body 原本沒有 `code`）與 `accessDeniedBecomesInternalServerError`（403 原本被 catch-all 吃成 500）變紅，其餘 15 條全綠——證明 Security 的改動沒有溢出到其他錯誤路徑。改寫後為 18 條（新增一條「憑證錯誤時 message 仍不透明」）。

### Phase 2 —— 消滅格式 #1（`GlobalExceptionHandler`，**下游風險最高**）

8 個自訂 handler 改回 `ProblemDetail`（handler 直接回 `ProblemDetail`，或改拋 `ErrorResponseException`）。

**要保留的既有設計**（這些是刻意的，不要在重構中弄掉）：
- **單點組裝漏斗**：現在的 `body()` / `respond()` 是「回應本體唯一入口」，只換型別、不換結構。
- **「預期 vs 未預期」的 log 策略**：`BusinessException` → WARN 一行無堆疊；`SystemException` / 兜底 → ERROR + 完整堆疊。
- **`tag` 一值兩用**：log 前綴 = 回應的 `code`，讓客戶端回報的碼可直接 grep log。
- **500 回應不透明**：`SystemException` 與 `handleUnexpected` 對外完全相同，差別只在 log 的資訊量。

同步收斂 `code` 命名空間（見 §6 表格中的 `writableStackTrace` 警告）。

**這裡才是真正的破壞性變更**：`message` → `detail`、`error` → `title` 直接打中下游 `expectError()` 的執行期斷言與 5 處字面比對（§5.4）。因此本 phase 必須與下游 `Playwright-TS` 的對應修改**成對上線**，不是「內部重構」。

**此 phase 完成時同步文件**：`CLAUDE.md`、`AGENTS.md`、`docs/agents/*`、`.github/instructions/Global.instructions.md`、`docs/handout/03-exception-handling.md`、`docs/swagger-openapi-design-guide.md` 原則 8。

**已完成的實作與過程中確認的事實：**

- **`ErrorCode` 從 3 個引數收斂為 2 個**（`HttpStatus` + `title`），代碼即 enum 名（`getCode()` 就是
  `name()`），並移入 `com.ibm.demo.exception`。§5.2 的命名空間分裂是這樣消滅的：不是把 `RATE_LIMITED`
  這些字面值搬進 enum 再各配一個編號，而是**讓「代碼」與「enum 名」成為同一件事** —— 於是不存在
  第二套命名法可以漂移。`SYS_001` / `PRODUCT_003` 風格的編號代碼因此全部消失。
- **`type` 由 `code` 機械推導**（`ErrorCode.typeOf()` → `urn:problem:<kebab-code>`），不是另一個可以獨立
  填錯的欄位。兩者結構上不可能不一致。
- **父類別處理的例外也補上 `code` / `type`**：覆寫 `ResponseEntityExceptionHandler` 的收尾 hook ——
  它是所有內建 handler 的**唯一出口**。在那裡補，比逐一覆寫十幾個 `handleXxx` 可靠：未來 Spring
  新增一種內建例外時自動涵蓋，不會靜默漏掉。（最初覆寫的是 `createResponseEntity(...)`，後續改為
  `handleExceptionInternal(...)` —— 見下方 Phase 6。）
- **`Content-Type` 不必手動指定**：`AbstractJacksonHttpMessageConverter.getSupportedMediaTypes(Class<?>)`
  依 **body 的執行期型別**選 media type，所以回 `ProblemDetail` 就是 `application/problem+json`，與
  `Accept` 無關（已讀原始碼確認，不是推測）。
- **`instance` 由框架填**：Spring 的回傳值處理器在寫回應時才從 `request.getRequestURI()` 補上。因此
  單元測試看到的必然是 `null`，只有契約測試（走真實 DispatcherServlet）看得到值 —— 在單元測試層斷言
  它只會驗到假的東西。
- **新增 `SystemException`** 區分「系統失敗」與「業務失敗」，並讓 `BusinessException` 建構子**拒絕
  非 4xx 的 `ErrorCode`**。§6 表格提到的 `writableStackTrace=false` 前提，就此從註解裡的口頭約定變成
  建構時可驗證的不變式。
- **`timestamp` 已移除**（§6 的決定）。

### Phase 3 —— `RestClientErrorHandler`（必須與 Phase 2 同一次上線）

否則跨 domain 錯誤訊息會靜默降級成 status text（§5.2）。

**先釐清一個常見誤解**：`ResponseEntityExceptionHandler` **不能**用在這裡，方向相反。它是 server-side（例外 → HTTP 回應）；`RestClientErrorHandler` 是 client-side（HTTP 錯誤回應 → 例外）。同理 `ErrorResponse` / `ErrorResponseException` 也都是 server-side 抽象——`RestClientResponseException` 的實作介面只有 `Serializable`，**刻意不實作** `ErrorResponse`。

**正解是 `DefaultResponseErrorHandler`**（`ResponseErrorHandler` 的預設實作）：

- 覆寫 `protected handleError(ClientHttpResponse, HttpStatusCode, URI, HttpMethod)`（Spring 6.2 起的簽章；舊的 `handleError(ClientHttpResponse)` 已被**移除**而非 deprecated）。
- 用 `getResponseBody(response)` + `initBodyConvertFunction(response, body)` 把解碼函式掛到例外上，之後 `ex.getResponseBodyAs(ProblemDetail.class)` 即可解 RFC 9457 body。
- 好處：**用 RestClient 自己那套 `HttpMessageConverters`**，不再有「獨立注入的 `ObjectMapper` 與實際 converters 兩套設定各自漂移」的問題（Jackson 3 module、naming strategy）。
- `ProblemDetail` 是現成型別，不需要自訂 DTO。
- 註冊方式現成：`RestClient.Builder` 有 `defaultStatusHandler(ResponseErrorHandler)` overload，所以 `RestClientConfig.java:85` 從
  `.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> errorHandler.handle(response))`
  改為 `.defaultStatusHandler(errorHandler)`。

**⚠️ 已讀原始碼確認的坑（不是假設）**：`handleError` 只在 `!CollectionUtils.isEmpty(this.messageConverters)` 時才呼叫 `ex.setBodyConvertFunction(...)`，而 `initBodyConvertFunction` 開頭就是 `Assert.state(!CollectionUtils.isEmpty(this.messageConverters), "Expected message converters")`。`RestClient` 走 `defaultStatusHandler(ResponseErrorHandler)` 是「adapted」路徑，**不會**幫你注入 converters。因此子類別**必須自行呼叫** `setMessageConverters(List.of(new JacksonJsonHttpMessageConverter(jsonMapper)))`，否則 `getResponseBodyAs(ProblemDetail.class)` 回 `null`。（Jackson 3 的 converter 建構子吃 `tools.jackson.databind.json.JsonMapper`，且繼承的 `application/*+json` 支援已涵蓋 `application/problem+json`。）

順手修既有缺陷：**空 `catch` 補上 log**（現在解析失敗完全無聲）。

（`ExtractingResponseErrorHandler` 是 `DefaultResponseErrorHandler` 既有子類別，但語意不符——它把 error body 抽成自訂 `RestClientException` 子類別，而這裡要的是依 status 分流成 domain 的 `BusinessException` / `SystemException`。）

**✅ 已完成，但範圍刻意縮小 —— 實際做的是最小正確變更：**

改用 **JSON tree** 讀 `detail`（`JsonMapper.readTree(...)` 後取 `detail` 節點），不綁任何 DTO；解析失敗
退回 `response.getStatusText()`，並把原本**空的、無 log** 的 `catch` 補上記錄。這樣一來：

- 不再綁死 `ApiErrorResponse`，也不會因為 problem body 日後多了 extension 欄位而炸；
- 不依賴 `ProblemDetailJacksonMixin` 是否註冊 —— 讀 tree 不需要任何 mixin，而 `RestClient` 手上的
  mapper 未必註冊過。`RestClientErrorHandlerTest` 刻意用**裸** `JsonMapper.builder().build()`，就是為了
  讓「不依賴 mixin」這件事被測到而不只是被相信。

**`extends DefaultResponseErrorHandler` 的遷移刻意延後**（不是忘記，理由如下）：

1. 它要求子類別自行 `setMessageConverters(...)`（見上方 ⚠️ 段落），等於把 converter 設定複製到第二個
   地方 —— 而「兩套設定各自漂移」正是要解決的問題本身。在 `RestClient` 願意把自己的 converters 交給
   adapted status handler 之前，換過去只是把漂移點搬家。
2. 目前只需要 `detail` 一個欄位，`getResponseBodyAs(ProblemDetail.class)` 的完整反序列化能力用不到。
   等到需要跨 domain 傳遞 `code`（呼叫端要依遠端 `ErrorCode` 分流）時再換，遷移才有回報。
3. 現行行為已被 6 個案例釘住（含 body 不可解析 / 合法 JSON 但缺 `detail` / 空 body 三種退化路徑），
   所以這個遷移可以是純內部替換，不必與對外契約變更綁在同一次上線。

### Phase 4 —— validation ✅ 已完成

改用 `errors` extension array；補 `getGlobalErrors()`；覆寫 `HandlerMethodValidationException`，讓 `@RequestParam` / `@PathVariable` 的驗證也走同一格式。

**已完成的實作與過程中確認的事實：**

- 新增 `exception/ValidationError`（`field` / `message`）。它與 `ApiErrorResponse` 不同 —— **會真的被
  序列化**，所以不存在「文件與 wire format 漂移」的空間。
- **`field` 缺席（而非 `null`、也不是空字串）代表「不屬於任何欄位」**：class-level 跨欄位約束的訊息
  該顯示在表單層級而非某個輸入框旁，呼叫端就是靠「有沒有 field」判斷。以 `@JsonInclude(NON_NULL)`
  實作，並由契約測試的 `empty()` 斷言釘住。
- **刻意不收 `rejectedValue`**：驗證失敗的輸入可能正是密碼或身分證號，不該回傳、也不該進 log；
  呼叫端本來就握有自己送出的內容，回傳它並無資訊增益。
- **`detail` 仍帶完整的欄位清單**，沒有換成「有 3 個欄位錯誤」這類摘要 —— 因為 `RestClientErrorHandler`
  跨 domain 只取 `detail`，摘要化等於在內部整合路徑上丟資訊。`detail` 與 `errors` 由**同一個
  `List<ValidationError>`** 產出（`ValidationError.describe()`），結構上不可能各說各話。
- 單筆格式從 `[field: message]` 改為 `field message`：global error 沒有欄位名，套上原格式會讀起來
  像有個叫做空字串的欄位。
- **覆寫 `handleHandlerMethodValidationException`**，讓參數上的約束失敗回出同一種形狀。目前沒有任何
  domain controller 會觸發它 —— 但探針端點讓它可測，且等到哪天有人在 `@RequestParam` 上加約束時，
  形狀分岔已經先被消滅，而不是在下游踩到才發現。Spring 7 的存取子是
  `getParameterValidationResults()` + `getCrossParameterValidationResults()`（**沒有**
  `getAllValidationResults()`）；結果為 `ParameterErrors` 時該參數本身是 bean，取 `getFieldErrors()`，
  否則欄位名就是參數名。（Phase 6 已把這段手寫分流換成框架的 `getBeanResults()` / `getValueResults()`
  —— 它們就是上述判斷的官方封裝。）

### Phase 5 —— 成功側衛生 ✅ 已完成

201 + `Location`、`DELETE` 統一 204、裸純量改小 DTO、`/exists` 改語意化端點、`PUT /order` 補 path variable。

**風險比原先評估的低**：`expectOk()` 只斷言 2xx 區間，所以 `200 → 201/204` 下游完全無感（§5.4）。真正會踩到下游的是**回應形狀**變化——建立類端點從裸數字改成 `{ "id": N }`（`expect(typeof accountId).toBe('number')` 這類斷言）與 `PUT /order` 的 path variable。也就是說本 phase 的下游影響面**明確且可枚舉**，不像 Phase 2 是全域欄位改名。

仍要注意 push `main` 的副作用順序——推快照排在 dispatch 之後，所以改 API 契約時下游必定先報一次「快照與 live spec 有差異」（見 `docs/agents/09-monitoring.md`）。

**已完成的實作與過程中確認的事實：**

- **§5.3 的六種形狀收斂為三種**：`PageResponse<T>`、裸 DTO、空 body（204）。裸 `Integer` 由
  `util/CreatedResponse`（201 + `Location`）取代、裸 `Boolean` 由 `OrderExistenceResponse` 取代、
  三個裸 `void` 的內部庫存端點改為 `ResponseEntity<Void>` + 204。
- **`Location` 用 `fromCurrentRequestUri()` 而非 `fromCurrentRequest()`**：後者會把 query string 併進
  `Location`，而新資源的位置與建立它時帶了什麼查詢參數無關。這件事沒有型別能保護，只由契約測試
  釘住（`locationExcludesQueryString`）。`CreatedResponse.at()` 的**前提**是「建立端點的 URI 就是集合
  URI」；日後若出現 `POST /account/{id}/xxx` 這種子資源建立，得自行組 `Location`。
- **順手修掉一個真的驗證破口**：`/product/reserve|release` 原本收裸 `Set<OrderItemRequest>`，而
  `@Valid @RequestBody Set<...>` 走的是 DataBinder 對「整個集合物件」驗證，**元素上的約束根本不會被
  套用**。包成 `StockChangeRequest`（`Set<@Valid OrderItemRequest> items`）後才與
  `CreateOrderRequest.items` 是同一種尋常 cascade。同時給 `OrderItemRequest` 的兩個欄位補上
  `@NotNull` / `@Positive` —— 先前它們只靠上游 DTO 的約束「順便」成立，是慣例而非型別保證。
  （`AdjustStockRequest.from` / `to` **刻意不加 `@NotEmpty`**：空 `from` 是純新增預留、空 `to` 是全數歸還。）
- **`/existence` 與 `/order-eligibility` 的形狀刻意不同**：前者 200 + body，因為 `true` 與 `false`
  都是「查詢成功」的答案；後者 204，因為不合格會拋錯，成功時沒有資訊可回。這不是不一致 —— 是同一條
  規則（有內容才回 body）套在兩種語意上的結果。
- **`updateOrder` 的 id 只有一個來源**：`UpdateOrderRequest` 移除 `orderId`，改由 `PUT /order/{orderId}`
  指定。兩邊都放會製造「path 與 body 不一致時聽誰的」這種只能靠額外規則回答的問題。
- **URI 改名一併做掉**：`/product/adjustStock` → `/adjust-stock`（camelCase 不是 URI 慣例）、
  `/order/account/{id}/exists` → `/existence`（名詞化，與既有的 `/order-eligibility` 一致）。
  兩者都**不在下游 endpoint map**（只有 account / product / order / testData），所以是下游安全的改名。
- **新增 `ApiSuccessContractTest`（15 個案例）**：與錯誤側刻意用不同做法 —— 錯誤格式與 domain 無關，
  所以用探針 controller；成功側的「201 / 204 / 200」是**每個端點各自的行為**，用探針只會驗到探針自己。
  因此這裡以 `@TestConfiguration @Import` 明確載入**真正的三個 domain controller**、service 以
  `@MockitoBean` 頂替。`Location` 的斷言不只比字串，還會真的 GET 一次那個 URI —— 否則測到的只是
  「字串組得出來」，而不是「它指向一個存在的資源」。
- **測試側改動的範圍就是契約變更的範圍**：`OrderServiceTest`、`AccountServiceTest`、
  `OrderDetailSoftDeleteIntegrationTest` 共 3 個檔案。`ProductServiceTest` 完全沒動 —— 因為
  `ProductService.reserveStock(Set<OrderItemRequest>)` 的簽章刻意沒變，`StockChangeRequest` 只是
  controller 層的 wire 形狀，沒有滲進 service。

### Phase 6 —— 少寫一點，讓父類別多做一點 ✅ 已完成

**契約零變更**（`ApiErrorContractTest` 21 個錯誤側案例一字未改仍全綠），所以嚴格說不屬於本文件的落地
路徑；記在這裡是因為它動了 Phase 2 建立的機制。

Phase 2 的目標是「先讓格式收斂」，做法上有多少手刻在所不計。回頭看，`ResponseEntityExceptionHandler`
的 javadoc 早就把可以動的地方講完了 —— 覆寫個別 `handleXxx`、覆寫 `handleExceptionInternal` 做所有例外
的共同處理、或覆寫 `createResponseEntity` 攔最後一步。本 phase 就是把手刻的部分挪回這三個位置：

- **共同處理從 `createResponseEntity` 移到 `handleExceptionInternal`，自訂 handler 也改為經由它**
  （不再自己 `new ResponseEntity`）。Phase 2 當時只有「框架攔到的」會經過收尾 hook，自訂 handler 是
  直接 `new ResponseEntity` 回去的 —— 現在兩條路都走同一段收尾（也因此都會經過 `createResponseEntity`）。
  換 hook 的原因只有一個：只有 `handleExceptionInternal` 同時看得到**例外**與**最終 status**，
  而「500 才印 stack trace」需要這兩個資訊。附帶拿到父類別「回應已 commit 就別硬寫第二份 body」的保護。
- **body 改用 `ErrorResponse.builder(...)`**，取代 `ProblemDetail.forStatusAndDetail` + 四行 setter。
  純粹是比較短，沒有別的用意；builder 上那組 `*MessageCode()`（訊息外部化）**刻意不接** —— 本專案沒有
  多語系需求，而放了 bundle 就等於讓回應隨 `Accept-Language` 而變，那是契約變更。
- **驗證錯誤的抽取改用框架 API**：`getAllErrors()` 取代「先 `getFieldErrors()` 再 `getGlobalErrors()`」
  （欄位名由 `FieldError` 這個型別本身帶出），`getBeanResults()`／`getValueResults()` 取代手寫的
  `instanceof ParameterErrors` 分流。
- **`handleSystemException` 併入 `Exception` 兜底**。Phase 2 已決定兩者對外回應必須相同，但當時是靠
  兩個 handler 各自維持一致（由測試看守）；共用一個 handler 後變成結構上不可能分岔，差異只剩 log。
- **刻意不做 `BusinessException implements ErrorResponse`**：那會讓每次 throw 都得配一個
  `ProblemDetail`，與 §6 表格裡「關掉 stack trace 是因為它走高頻控制流」的前提相衝；也會把 `code` 這個
  wire 欄位名推進 `exception` package。而 `RestClientErrorHandler` 也會建 `BusinessException`，那個情境
  根本不產生 HTTP 回應。收益是省下 handler 裡的一行。

**驗證方式**：`./gradlew test -Djunit.platform.exclude.tags=SanityTest` 全綠（190 個測試），其中
`ApiErrorContractTest` 未修改 —— 逐位元組比對過的 wire format 沒有漂移。`GlobalExceptionHandlerTest`
有改，但改的是**簽章**（`HttpServletRequest` → `WebRequest`、`ResponseEntity<ProblemDetail>` →
`ResponseEntity<Object>`）與合併後的 500 測試，斷言的字串一字未動。

---

## 8. 非目標

- **不導入成功回應信封**（立場 B / C 已否決，理由見 §3）。
- **不改 `util/PageResponse.java`**（見 §3 末段）。`docs/pagination-strategies-guide.md` 的 `CursorPageResponse<T>` 提案與本 ADR 無關，各自獨立。
- **不在本階段導入 `traceId` / Micrometer Tracing**（列為後續選項）。
- ~~**不在本 ADR 生效時同步 `CLAUDE.md` / `AGENTS.md`**~~ —— Phase 5 完成後已一併同步，因此此項不再是非目標。實際更新的檔案：`CLAUDE.md`、`docs/agents/06-architecture.md`、`docs/agents/10-troubleshooting.md`、`docs/handout/HANDOUT.md`、`docs/handout/01-architecture-layering.md`、`docs/handout/03-exception-handling.md`（§1／§3／§4 重寫，「已知不一致」改記為已解決）、`docs/swagger-openapi-design-guide.md`（原則 8／12 與現況評估表）、`docs/resilience4j-configuration-guide.md`、`docs/security-external-idp-migration.md`（端點改名）、`筆記.md`、`new-domain-scaffold` skill（`.claude` 與 `.bob` 兩份，內容相同）。
  `AGENTS.md` 本身只有 `@`-import、不含契約內容，`.github/instructions/Global.instructions.md` 也只有回應語言／指令執行／文檔管理三段而無 API 契約敘述 —— 兩者確認無需改動。

---

## 9. 參考

- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)（取代 RFC 7807）
- [Zalando RESTful API Guidelines](https://opensource.zalando.com/restful-api-guidelines/) —— MUST 支援 problem JSON
- [Microsoft REST API Guidelines](https://github.com/microsoft/api-guidelines) —— 錯誤格式與 status code 使用
- [Google API Improvement Proposals](https://google.aip.dev/) —— AIP-193（errors）、AIP-158（pagination）
- 專案內相關文件：
  - [`docs/handout/03-exception-handling.md`](handout/03-exception-handling.md) —— 現行例外處理設計與已知不一致
  - [`docs/swagger-openapi-design-guide.md`](swagger-openapi-design-guide.md) —— 原則 8（Error Response 標準化）、原則 12（Response Wrapper）
  - [`docs/security-external-idp-migration.md`](security-external-idp-migration.md) —— 與 Phase 1 有交集（authN/authZ 外包後 401/403 的產生位置會再變一次）
