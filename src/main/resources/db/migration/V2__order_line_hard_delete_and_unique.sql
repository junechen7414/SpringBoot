-- ============================================================================
-- Flyway Migration V2: Order Line hard delete + uniqueness
-- ============================================================================
-- 背景見 docs/adr/0001-soft-delete-only-for-termination.md：
-- 更新 Order 時移除的 Order Line 改為實體刪除，DB 才能用一般的 UNIQUE 約束
-- 守住「一張 Order 裡同一個 Product 只有一行 Order Line」。
--
-- 本專案沒有正式環境的資料，所以不做資料修補；只確保在 dev 的舊資料上也能執行成功。
-- Compatible with both H2 (Oracle mode) and Oracle databases.
-- ============================================================================

-- 1. 先清掉已軟刪除的 Order Line：dev 的舊資料可能有同一組 (Order, Product) 的重複列，
--    不先刪掉的話，下一步加 UNIQUE 會失敗。
DELETE FROM ORDER_PRODUCT_DETAIL WHERE DELETED = TRUE;

-- 2. 唯一約束。ORDER_ID 是第一個欄位，所以它的索引同時充當 FK_DETAIL_ORDER 的索引
--    （Oracle 不會自動替 FK 建索引），不另外建。
ALTER TABLE ORDER_PRODUCT_DETAIL
    ADD CONSTRAINT UK_DETAIL_ORDER_PRODUCT UNIQUE (ORDER_ID, PRODUCT_ID);

-- 3. 只允許 OrderStatus 定義的值（1001=CREATED, 1003=CANCELLED）。
ALTER TABLE ORDER_INFO
    ADD CONSTRAINT CK_ORDER_STATUS CHECK (STATUS IN (1001, 1003));

-- 4. Order Line 不再軟刪除，移除軟刪欄位。
ALTER TABLE ORDER_PRODUCT_DETAIL DROP COLUMN DELETED;
ALTER TABLE ORDER_PRODUCT_DETAIL DROP COLUMN DELETED_AT;

-- ============================================================================
-- End of Migration V2
-- ============================================================================
