# 終止狀態只由 DELETED 記錄，不另設 status

ADR 0001 把軟刪除收窄到三個終止動作（Deactivate、Delist、Cancel），而這三個動作之後都只能走 `DELETE`。但 `DELETE` 原本會在同一條 UPDATE 裡同時寫兩個欄位：status（Account 的 `STATUS = 'N'`、Product 的 `SALE_STATUS = 1002`、Order 的 `STATUS = 1003`）和 `DELETED = true`。既然沒有其他路徑能單獨改 status，兩個欄位一定同時變動，等於重複記錄同一件事；而 `@SQLRestriction` 會把終止的資料濾掉，回應裡的 status 永遠是「啟用」那個值，不承載任何資訊。決定以 `DELETED`／`DELETED_AT` 為唯一的終止紀錄，移除三個 entity 的 status 欄位（V3 migration）。之後若某個 entity 真的需要多個「活著」的業務狀態（例如 Order 的 PAID、SHIPPED），再在那個 entity 加回 status，專門表達業務狀態，不再兼任終止標記。

## Considered Options

- **留 `DELETED`，移除 status**（採用）：三個 domain 共用同一套 `SoftDeleteMetadata`／`SoftDeleteRepository`，`@SQLRestriction` 都是 `DELETED = false`，簡單一致。
- **留 status，移除 `DELETED`**：每個 domain 的終止值都不一樣（`'N'`／1002／1003），共用的軟刪除機制得拆掉或重寫；`DELETED_AT` 也要另外決定去留。
- **兩個都留，分工**：`DELETED` 表示存不存在，status 留給未來的業務狀態。不用 migration，但 status 目前只剩一個值，重複資訊只是換成一個沒人寫的欄位。

## Consequences

- V3 migration 先把「只靠舊的 `PUT` 改 status 停用、`DELETED` 仍是 false」的資料補成 `DELETED = true`，再 drop 欄位；否則這些已終止的資料會在 drop 之後重新出現。以前用 `PUT` 改成取消的訂單沒有釋放 Reservation，這段補正補不回那些庫存。
- `AccountStatus`、`ProductStatus`、`OrderStatus` 與 `ErrorCode.ORDER_STATUS_INVALID` 一併移除。對外回應不再有 `status`／`saleStatus`／`orderStatus`（破壞性變更，PR #79）。
- 已終止的資料在 `findById` 就被濾掉，所以「再終止一次」一律回 404（`SoftDeleteIntegrationTest` 釘住），不需要另外的狀態檢查。
- 正式 DTO 目前沒有 enum 欄位；`GlobalExceptionHandler` 對 enum 值域錯誤的轉譯改由測試裡的 `ContractProbeController.ProbeStatus` 釘住。日後加回 status 時，仍照 `docs/swagger-openapi-design-guide.md`「改善 4」的做法把 DTO 欄位宣告成 enum。
- V3 migration 的註解指向 `.scratch/termination-flag-redundancy/issues/01-status-and-deleted-record-the-same-fact.md`，那份 ticket 已在結案後刪除，內容以本 ADR 為準（原文可從 git 歷史取得）。已套用的 migration 不能修改：Flyway 會比對 checksum，連註解改了都會讓 validate 失敗。
