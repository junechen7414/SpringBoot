# 終止動作同時寫 status 與 DELETED，兩個欄位記同一件事

Status: resolved（PR 審查中）

## 背景

`.scratch/order-lifecycle` 那批改動完成後，`PUT` 不能再改 Account、Product、Order 的 status，終止只能走 `DELETE`（見 `CONTEXT.md` 的 Deactivate、Delist、Cancel，以及 `docs/adr/0001-soft-delete-only-for-termination.md`）。

但 `DELETE` 會在同一條 UPDATE 裡同時寫兩個欄位：

| Domain | status | 軟刪除 |
|---|---|---|
| Account | `STATUS = 'N'` | `DELETED = true` |
| Product | `SALE_STATUS = 1002` | `DELETED = true` |
| Order | `STATUS = 1003` | `DELETED = true` |

（`AccountRepository`、`ProductRepository`、`OrderRepository` 的 `softDeleteById`）

既然沒有其他路徑能單獨改 status，這兩個欄位以後一定同時變動，等於重複記錄同一件事。各 entity 的 `@SQLRestriction` 也同時檢查這兩個欄位。

## 待決定

- 要以哪一個欄位為準：只留 status，還是只留 `DELETED`／`DELETED_AT`？
- 只剩一個值的 status 欄位（例如 Account 只剩 `Y`）還有沒有存在的意義？
- 範圍是三個 domain 的 migration，屬於高風險改動，要走 branch + PR。

## 一併處理：entity 的 status 改成 enum

三個 entity 的 status 欄位目前仍是 `Integer`／`String`，Service 要自己呼叫 `fromCode`／`getCode` 轉換（DTO 已經用 enum 了）。如果上面的決定是保留 status 欄位，就順便把 entity 欄位改成 enum：

- 用 `@Enumerated` 加 `@EnumeratedValue`（Jakarta Persistence 3.2，專案用的 3.2.0 與 Hibernate 7.4 都支援），**不用 `@Convert`**。三個 enum 都是「一個常數對應一個固定的值」，不需要另寫 converter class。
  - `OrderStatus`、`ProductStatus` 的 `int code` 搭配 `EnumType.ORDINAL`；`AccountStatus` 的 `String code` 搭配 `EnumType.STRING`。
  - `@EnumeratedValue` 標註的欄位必須是 `final`。
- 各 repository 的 `softDeleteById` 現在是把數字或字元直接拼進 JPQL（例如 `o.status = 1003`），欄位改成 enum 之後要改寫。`@SQLRestriction` 是原生 SQL，不受影響。
- 這件事原本列在 order-lifecycle 的 01，因為 `PUT` 拿掉 status 之後收益變小，而且 status 欄位可能整個被移除，所以移到這裡。

## 為何延後

2026-10-05 的 grill session 刻意不把這件事併進 `order-lifecycle`，以免範圍擴大。使用者要求：**這件事一定要回來處理，不能漏掉。**

## Comments

- 2026-10-13：決定**以 `DELETED`／`DELETED_AT` 為準，移除 status**（使用者確認）。之後某個 entity 真的需要多個「活著」的業務狀態時，再在那個 entity 加回 status 欄位。demo 用途以簡單一致為優先。
  - **V3 migration**（`V3__drop_redundant_status_columns.sql`）：
    - 先補正「只靠 PUT 改 status 停用、`DELETED` 仍是 false」的舊資料，不補的話 drop 之後它們會重新出現。
    - 接著 drop `CK_ORDER_STATUS`，再 drop `ACCOUNT.STATUS`、`PRODUCT.SALE_STATUS`、`ORDER_INFO.STATUS`。
    - 已驗證能套用：Oracle 由 Testcontainers 整合測試涵蓋；H2 用 `e2e` profile 起 app，Flyway 依序套用 V1–V3，`ddl-auto: validate` 也通過。
  - **程式碼**：
    - 三個 entity 的 `@SQLRestriction` 都只剩 `DELETED = false`；`softDeleteById` 不再寫 status。
    - 刪除 `AccountStatus`、`ProductStatus`、`OrderStatus`，以及沒人呼叫的 `findByStatus`／`findBySaleStatus`。
    - 刪除 `ErrorCode.ORDER_STATUS_INVALID`，連同 `prepareOrderDeletion` 裡那段不可達的狀態檢查：已取消的訂單在 `findById` 就被濾掉。
  - **「entity 的 status 改成 enum」**：status 已經整個移除，這部分不用做了。
  - **API 契約（破壞性）**：六個回應拿掉 status 欄位：
    - `GetAccountDetail/ListResponse.status`
    - `GetProductDetail/ListResponse.saleStatus`
    - `GetOrderDetailResponse.orderStatus`
    - `GetOrderListResponse.status`
    - 這些欄位原本永遠是「啟用」那個值（終止的資料會被濾掉），沒有承載資訊。
    - 下游已在合併前先移除依賴（`Playwright-TS` 的 `7c3f52b`）：拿掉斷言只會讓新舊 image 都通過，所以這次的順序是下游先 push。
  - **測試**：
    - `SqlStatusIntegrationTest` 改寫並改名為 `SoftDeleteIntegrationTest`，改成驗證軟刪除後查不到、但資料列還在，且 `DELETED`／`DELETED_AT` 都已寫入。
    - `GlobalExceptionHandler` 對 enum 值域錯誤的轉譯，改由 `ContractProbeController.ProbeStatus` 這個探針 enum 釘住。
    - `./gradlew test -Djunit.platform.exclude.tags=SanityTest`：202 個測試全數通過。和之前比，刪掉了 5 個只驗證 status 的測試；之前回報的 211 多算了殘留的舊結果檔，實際是 207。
