# 軟刪除只用於終止動作，不用於 Order Line

本專案原本以「soft-delete-everywhere」為原則：更新 Order 時被移除的 Order Line 也做軟刪除，而且是在 `584dd79` 由實體刪除改成軟刪除，理由只是要和這條原則一致。現在決定收窄範圍：軟刪除只用來實作三個終止動作，也就是 Account 的 Deactivate、Product 的 Delist、Order 的 Cancel（三者都不可恢復，終止後對系統而言不再存在）。Order Line 沒有自己的終止動作，更新 Order 時被移除的 Order Line 直接實體刪除。理由是：Order Line 的軟刪除只留下「被移除過」的列，數量變更是原地覆寫的，所以拼不出 Order 過去的樣子。這些列沒有任何讀取端，卻逼得「一張 Order 裡每個 Product 只有一行」這條不變量只能用「僅限未刪除列」的唯一索引來實作（H2 不支援 function-based index，得改用 generated column）。

## Considered Options

- **維持 Order Line 軟刪除，加 generated column 唯一索引**：H2 2.4.240 上實際跑過可行。但這等於付出結構上的複雜度，去保留一份不完整的歷史。
- **Order Line 實體刪除，加一般的 `UNIQUE(ORDER_ID, PRODUCT_ID)`**（採用）：這個唯一索引以 `ORDER_ID` 為第一個欄位，同時可以充當 FK 索引。

## Consequences

- 如果將來需要「Order 曾經長什麼樣子」，應該另外設計（例如 Order Revision 或稽核表），**不要**把 Order Line 改回軟刪除。
- 移除 Order Line 仍然會逐筆檢查樂觀鎖：Hibernate 刪除帶 `@Version` 的 entity 時，預設就是 `WHERE ID = ? AND VERSION = ?`。
- 各 entity 上沒有呼叫端的 `restore()` 一併移除，因為終止動作不可恢復。
- Order 被 Cancel 時，它的 Order Line 原封不動地保留，跟著 Order 一起封存，不另外刪除。所以實體刪除只發生在「更新 Order 時移除某個 Order Line」這一種情況。
- **Cancel 維持軟刪除，不改成實體刪除**（2026-10-06 討論過）。軟刪除在這裡的目的是保留紀錄，不是為了日後復原。「訂單不復原、要買就重新建立」這點成立，但軟刪或實刪都一樣成立，不能拿來支持實體刪除。保留被取消的 Order 有三個理由：
  1. `deleteOrder` 的補償失敗、需要人工介入時，要查得到這張單原本有哪些 Product 與數量。
  2. trace、log、下游系統可能存著 order ID，實體刪除後這些參照就對不到資料。
  3. 客訴、取消率、詐欺分析這類需求在訂單領域幾乎一定會出現。資料刪掉就找不回來，留著不用的代價只是表大一些。

  這和 Order Line 改為實體刪除不衝突：被移除的 Order Line 只是殘缺的歷史（數量是原地覆寫的），被取消的 Order 則是完整的業務事件快照。
- 終止狀態由哪個欄位記錄，見 ADR 0002。
