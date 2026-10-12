# 改名：`OrderInfo` 改成 `Order`、`OrderDetail` 改成 `OrderLine`

Status: resolved
Blocked by: 01

背景見 `../spec.md`；用語見 `CONTEXT.md`。

## 要做的事

- `OrderInfo` 改成 `Order`，加上 `@Entity(name = "CustomerOrder")`。`ORDER` 是 JPQL 保留字，JPA 規格不允許拿來當 entity name。所有 `@Query` 裡的 `FROM OrderInfo` 改成 `FROM CustomerOrder`，並在 entity 上加一行註解，說明為什麼 entity name 和 class 名稱不同。
- `OrderDetail` 改成 `OrderLine`；`OrderInfoRepository`／`OrderDetailRepository` 改成 `OrderRepository`／`OrderLineRepository`；相關的欄位、方法和 DTO（例如 `orderDetails`、`addOrderDetail`、`CreateOrderDetailRequest`、`UpdateOrderDetailRequest`、`OrderItemDTO`）也一起改成 Order Line 的用語。
- **資料表名稱和 API 的 JSON 欄位名稱都不改**。如果 DTO 改名會影響 JSON 欄位名稱或 OpenAPI schema 名稱，就保留原本的欄位名稱，或用 `@Schema(name = ...)` 固定 schema 名稱。改完要比對 `swagger.json`，確認對外契約沒有變。
- 同一個檔案如果同時用到 Spring Data 的 `Sort.Order`，要寫完整類別名稱。
- 順便處理兩個舊問題：
  - `OrderService` 開頭的 Javadoc 有一個 `@param orderDetailRepository`，但這個類別沒有這個欄位。
  - `OrderTransactionalServiceTest` 用 `List.of(...)` 當 `setOrderDetails` 的 fixture，這是不可變集合，之後若有測試呼叫 `addOrderLine` 會拋 `UnsupportedOperationException`。改用可變的 list。
- 同步更新 `筆記.md` 和 `docs/handout/02-persistence-jpa.md` 裡引用這些舊名稱的程式片段。

## 驗收

- 改名前後的 `swagger.json` 沒有差異。
- `./gradlew test` 全部通過。

## Comments

- 2026-10-12：直接在 `main` 實作（單一 domain 的改名，沒有 migration；編譯、測試和 spec 比對都能一次驗證）。
  - class 改名：`OrderInfo` → `Order`（`@Entity(name = "CustomerOrder")`，JPQL 改成 `FROM`／`UPDATE CustomerOrder`），`OrderDetail` → `OrderLine`，`OrderInfoRepository` → `OrderRepository`。
  - 欄位與方法：`orderDetails` → `orderLines`，`addOrderDetail` → `addOrderLine`，`OrderLine.orderInfo` → `OrderLine.order`（`mappedBy = "order"`），`findByIdWithDetails` → `findByIdWithLines`。
  - DTO：`CreateOrderDetailRequest` → `CreateOrderLineRequest`，`UpdateOrderDetailRequest` → `UpdateOrderLineRequest`，`OrderItemDTO` → `OrderLineResponse`。三個都用 `@Schema(name = ...)` 固定成舊的 schema 名稱。
  - **刻意不改**：`GetOrderDetailResponse`、`getOrderDetailByOrderId`、`OrderController.getOrderDetails`。這裡的 detail 是「訂單詳情」，跟 `GetProductDetailResponse` 同一種命名，不是 Order Line。
  - `OrderLine` 上 `@JoinColumn(name = "ORDER_ID")` 的註解前提改變了：屬性改叫 `order` 之後，預設推導剛好就是 `order_id`。保留明寫，並改寫註解說明原因。
  - 順手處理的兩個舊問題：`OrderService` 建構子的 Javadoc（`@param orderDetailRepository` 已在 01 移除，這次補上缺的 `orderTransactionalService`）；`OrderTransactionalServiceTest` 的 fixture 改成 `new ArrayList<>(List.of(...))`。
  - 同步更新 `筆記.md`、`docs/handout/01-architecture-layering.md`、`docs/handout/02-persistence-jpa.md`、`docs/swagger-openapi-design-guide.md`。handout 裡 `@Entity` 搭配 `Order` 的片段一律加上 `name = "CustomerOrder"`，避免照抄後 JPQL 解析失敗。描述歷史做法的句子保留舊名稱，並標明「改名前」。
  - `swagger.json`：改名前後完全相同。8787 當時被 `spring-boot-app-test` 佔用，`generateOpenApiDocs` 會抓到那個容器的舊 spec，所以改用 `bootRun --server.port=8790` 搭配 `openapi` profile 直接抓 `/v3/api-docs`，再跟改名前的 `build/docs/swagger.json` 比對。
  - `./gradlew test -Djunit.platform.exclude.tags=SanityTest`：211 個測試全數通過。
