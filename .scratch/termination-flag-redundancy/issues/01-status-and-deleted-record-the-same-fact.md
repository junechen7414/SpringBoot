# 終止動作同時寫 status 與 DELETED，兩個欄位記同一件事

Status: needs-triage

## 背景

`.scratch/order-lifecycle` 那批改動完成後，`PUT` 不能再改 Account、Product、Order 的 status，終止只能走 `DELETE`（見 `CONTEXT.md` 的 Deactivate、Delist、Cancel，以及 `docs/adr/0001-soft-delete-only-for-termination.md`）。

但 `DELETE` 會在同一條 UPDATE 裡同時寫兩個欄位：

| Domain | status | 軟刪除 |
|---|---|---|
| Account | `STATUS = 'N'` | `DELETED = true` |
| Product | `SALE_STATUS = 1002` | `DELETED = true` |
| Order | `STATUS = 1003` | `DELETED = true` |

（`AccountRepository`、`ProductRepository`、`OrderInfoRepository` 的 `softDeleteById`）

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
