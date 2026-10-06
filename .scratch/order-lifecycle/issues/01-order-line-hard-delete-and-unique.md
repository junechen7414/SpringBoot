# Order：Order Line 改為實體刪除、加上唯一約束，`PUT` 拿掉 status

Status: ready-for-agent

背景與決策見 `../spec.md`、`docs/adr/0001-soft-delete-only-for-termination.md`。

**高風險（DB migration 與 API 契約）：走 `high-risk-pr-workflow`。**

## 要做的事

### V2 migration（`src/main/resources/db/migration/V2__...sql`）

照下面的順序執行：

1. 刪除 `ORDER_PRODUCT_DETAIL` 中 `DELETED = true` 的列。dev 的 compose volume 裡可能有同一組 (Order, Product) 的重複列，不先刪掉的話，下一步加 `UNIQUE` 會失敗。
2. 加上 `UNIQUE (ORDER_ID, PRODUCT_ID)`。
3. 在 `ORDER_INFO` 加上 `CHECK (STATUS IN (1001, 1003))`。
4. drop `ORDER_PRODUCT_DETAIL` 的 `DELETED`、`DELETED_AT`。

在 migration 開頭用註解寫明：本專案沒有正式環境的資料，所以不做資料修補。

這支 migration 必須在 Oracle（整合測試）和 H2 Oracle mode（`openapi` profile 會跑 Flyway）上都能執行。四個步驟都是標準 DDL，應該沒有相容性問題，但兩邊都要實際跑過。

### Order Line entity（`OrderDetail`）

- 移除 `@SQLDelete`、`@SQLRestriction("DELETED = false")`、`SoftDeleteMetadata`、`restore()`。`orphanRemoval` 改成直接觸發實體刪除；Hibernate 預設的刪除語句是 `WHERE ID = ? AND VERSION = ?`，所以仍然會逐筆檢查樂觀鎖。
- 移除 `OrderDetailRepository.softDeleteByOrderId` 和它在 `DELETE /order/{id}` 流程裡的呼叫。Cancel 時 **Order Line 保留不動**：Order 這一列保留但查不到，它的 Order Line 跟著原封不動地保留，符合 ADR 0001「終止 = 保留資料列，只是對系統不可見」。不要改成實體刪除，否則被取消的單買了什麼就查不回來了。
- `OrderInfoRepository.findByIdWithDetails` 上 LEFT JOIN 的註解，前提是「明細有軟刪除」。改完之後，要依照新的設計更新這段註解（為什麼仍然要用 LEFT JOIN：Order 可能沒有任何 Order Line）。

### Order

- `UpdateOrderRequest` 拿掉 `orderStatus`；`OrderTransactionalService.updateOrder` 不再呼叫 `setStatus`。
- 移除 `OrderInfo.restore()`。
- 「Cancel 之後查詢和再 `DELETE` 都回 404」維持現狀，不用改。

## 驗收

- 同一張 Order 寫入兩行相同 Product 的 Order Line 時，DB 會擋下（整合測試）。
- 更新 Order 時移除某個 Order Line，之後再加回同一個 Product 可以成功，而且 DB 裡只有一列。
- 移除 Order Line 遇到過期的 version 時，會拋出樂觀鎖例外。
- `DELETE /order/{id}` 之後，該 Order 的 Order Line 仍然保留在 DB 裡（整合測試直接查資料表），而且 Reservation 已經釋放。
- `PUT /order/{id}` 的 body 不再有 `orderStatus`；`swagger.json` 已更新（`openapi-doc-gen`）。
- `OrderDetailSoftDeleteIntegrationTest` 改寫或刪除：它現在斷言的是「軟刪除」和「重新加入同一個 Product 會產生多列」，這兩件事都和新設計相反。
- `./gradlew test` 全部通過，`SqlStatusIntegrationTest` 也要通過。

## 注意

- 下游 E2E 如果在 `PUT /order` 的 body 裡帶了 `orderStatus`：Spring Boot 預設會忽略未知欄位（`FAIL_ON_UNKNOWN_PROPERTIES = false`），實作時要確認專案沒有改過這個設定。如果改過，這就是破壞性變更。

## Comments

- 2026-10-05：實作於 PR #78（`refactor/order-line-hard-delete-and-unique`）。`./gradlew test` 全綠（含 `SqlStatusIntegrationTest`、新的 `OrderLineIntegrationTest`）；V2 在 Oracle（Testcontainers）與 H2（`openapi` profile）都套用成功；`swagger.json` 的 `UpdateOrderRequest` 只剩 `items`。專案沒有覆寫 `FAIL_ON_UNKNOWN_PROPERTIES`，帶舊欄位的呼叫端不會 400。`OrderDetailRepository` 移除 `softDeleteByOrderId` 後變空，已一併刪除。文件同步留給 issue 05。
- 2026-10-06：討論「Cancel 是否也改成實體刪除」，結論是**維持軟刪除，不改**。
  - **軟刪除的目的是保留紀錄（audit），不是為了日後復原。** Deactivate／Delist／Cancel 三者都不可恢復（`restore()` 因此移除），軟刪除是「保留資料列，只是對系統不可見」（ADR 0001）。
  - 「訂單不復原、要買就重新建立，庫存只從 reserve／release 進出」：這點成立，但軟刪或實刪都一樣成立，因為本來就沒有復原的入口，所以不能拿來支持實體刪除。
  - 保留被取消訂單的理由：(1) `deleteOrder` 補償失敗、需要人工介入時，要查得到這張單原本有哪些商品與數量；(2) trace、log、下游系統可能存著 order ID，實體刪除後這些參照就對不到資料；(3) 客訴、取消率、詐欺分析這類需求在訂單領域幾乎一定會出現。資料刪掉就找不回來，留著不用的代價只是表會大一些。
  - 這和 Order Line 改為實體刪除不衝突：被移除的 Order Line 只是殘缺的歷史（數量是原地覆寫的），被取消的 Order 則是完整的業務事件快照。
  - 若日後真要消除 `STATUS = 1003` 與 `DELETED` 的重複，折衷做法是保留資料列、拿掉 `DELETED`、只靠 `STATUS = CANCELLED` 表示取消，交給 `.scratch/termination-flag-redundancy/issues/01-status-and-deleted-record-the-same-fact.md` 處理，不另開實體刪除的方向。
  - 附帶發現：「Cancel 之後 `PUT /order/{id}` 回 404」目前由 `OrderInfo` 的 `@SQLRestriction` 保證（`loadOrderView` 會在 `adjustStock` 之前就拋 404，不會動到庫存），但沒有測試釘住；`SqlStatusIntegrationTest` 只釘了「再 `DELETE` 回 404」。
