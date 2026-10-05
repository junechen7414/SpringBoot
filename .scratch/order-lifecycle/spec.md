# Order lifecycle：終止動作收斂與 Order Line 唯一性

2026-10-05 grill session 的結論。用語以 `CONTEXT.md` 為準，軟刪除的範圍見 `docs/adr/0001-soft-delete-only-for-termination.md`。

## 問題

1. **一個資源有兩條路可以消失。** Account、Product、Order 都能用 `PUT` 改 status 讓自己消失（之後查詢回 404、不可恢復），也能用 `DELETE`。Order 走 `PUT` 改成 `1003` 時**不會釋放 Reservation**，庫存會永遠卡在保留量裡。
2. **「一張 Order 裡同一個 Product 只有一行 Order Line」只靠 Java 層把關**（`OrderService.validateAndConvertToUniqueItems`），DB 沒有約束。原因是更新 Order 時移除的 Order Line 會被軟刪除，同一組 (Order, Product) 會累積多列，一般的唯一約束加不上去。
3. `ORDER_PRODUCT_DETAIL.ORDER_ID` 沒有索引（Oracle 不會自動替 FK 建索引）。

## 決策

- **終止只走 `DELETE`。** 三個 domain 的 `PUT` 都拿掉 status。`DELETE` 的行為維持現狀：軟刪除、status 設為終止值；Order 另外會釋放 Reservation。終止之後，再查詢或再 `DELETE` 都回 404，且不可恢復。
- **終止有先後限制。** 還有 Active Order 的 Account 不能 Deactivate（現有規則）；還有 Reservation 的 Product 不能 Delist（新規則，Product 只看自己的保留量，不依賴 order）。
- **Order 被 Cancel 時，Order Line 保留不動**，跟著 Order 一起封存（不再呼叫 `softDeleteByOrderId`）。
- **更新 Order 時移除的 Order Line 改為實體刪除**，DB 加一般的 `UNIQUE(ORDER_ID, PRODUCT_ID)`。這個唯一索引以 `ORDER_ID` 為第一個欄位，同時可以充當 FK 索引，所以不另建索引；也沒有任何查詢依 `PRODUCT_ID` 篩選，所以 `PRODUCT_ID` 不建索引。
- **保留三條 FK。** 本專案只做軟刪除，父表的列不會被實體刪除，所以 FK 不會擋到任何業務操作。
- **加 `CHECK (STATUS IN (1001, 1003))`** 在 `ORDER_INFO`。
- **不保留舊資料。** 本專案沒有正式環境的資料，migration 不做資料修補，只確保在 dev 的舊資料上也能執行成功。
- **移除四個 entity 上沒有呼叫端的 `restore()`**，因為終止動作不可恢復。
- **class 改名對齊 glossary：** `OrderInfo` 改成 `Order`，加上 `@Entity(name = "CustomerOrder")`，因為 `ORDER` 是 JPQL 保留字，JPA 規格不允許拿來當 entity name；`OrderDetail` 改成 `OrderLine`。資料表名稱不變。

## 刻意不做的事

- **Fulfillment（出貨或結單）**：本系統沒有這個概念，Reservation 不會轉成「已售出」。
- **Order 修改歷史**：需要的話另外設計（Order Revision 或稽核表），不要把 Order Line 改回軟刪除（見 ADR 0001）。
- **status 和 `DELETED` 重複記錄終止**，以及 entity 的 status 改成 enum：已延後，**但不能漏掉**，見 `.scratch/termination-flag-redundancy/issues/01-status-and-deleted-record-the-same-fact.md`。這批完成時要回來處理。

## Issues

| # | 檔案 | 要先完成 |
|---|---|---|
| 01 | `issues/01-order-line-hard-delete-and-unique.md` | — |
| 02 | `issues/02-account-put-drops-status.md` | — |
| 03 | `issues/03-product-put-drops-status-and-guard-delist.md` | — |
| 04 | `issues/04-rename-order-and-order-line.md` | 01 |
| 05 | `issues/05-docs-point-to-adr-0001.md` | 01、02、03 |
