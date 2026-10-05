# 改名：`OrderInfo` 改成 `Order`、`OrderDetail` 改成 `OrderLine`

Status: ready-for-agent
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
