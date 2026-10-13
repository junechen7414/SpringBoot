-- ============================================================================
-- Flyway Migration V3: drop status columns that duplicate DELETED
-- ============================================================================
-- 背景見 .scratch/termination-flag-redundancy/issues/01-status-and-deleted-record-the-same-fact.md：
-- 終止（Deactivate／Delist／Cancel）只走 DELETE，而 DELETE 在同一條 UPDATE 裡同時寫 status 與
-- DELETED，兩個欄位記的是同一件事。終止狀態統一由 DELETED／DELETED_AT 記錄（ADR 0001），
-- status 欄位移除；之後若某個 entity 需要多個「活著」的業務狀態，再各自加回 status。
--
-- Compatible with both H2 (Oracle mode) and Oracle databases.
-- ============================================================================

-- 1. 補正資料：issue 02／03 之前，PUT 可以只改 status 來停用資料，這些列的 DELETED 仍是 false。
--    不先補上的話，drop status 之後它們會重新出現在查詢裡。
--    （和 V2「不做資料修補」不同：這裡不補的後果是已終止的資料復活，而成本只是三條 UPDATE。
--    以前用 PUT 改成取消的訂單沒有釋放保留量，這段補正補不回那些庫存。）
UPDATE ACCOUNT
   SET DELETED = TRUE, DELETED_AT = COALESCE(DELETED_AT, CURRENT_TIMESTAMP)
 WHERE STATUS <> 'Y' AND DELETED = FALSE;

UPDATE PRODUCT
   SET DELETED = TRUE, DELETED_AT = COALESCE(DELETED_AT, CURRENT_TIMESTAMP)
 WHERE SALE_STATUS <> 1001 AND DELETED = FALSE;

UPDATE ORDER_INFO
   SET DELETED = TRUE, DELETED_AT = COALESCE(DELETED_AT, CURRENT_TIMESTAMP)
 WHERE STATUS <> 1001 AND DELETED = FALSE;

-- 2. 先拿掉 V2 加的 CHECK，再 drop 欄位：被約束引用的欄位，H2 會拒絕 drop。
ALTER TABLE ORDER_INFO DROP CONSTRAINT CK_ORDER_STATUS;

ALTER TABLE ACCOUNT DROP COLUMN STATUS;
ALTER TABLE PRODUCT DROP COLUMN SALE_STATUS;
ALTER TABLE ORDER_INFO DROP COLUMN STATUS;
