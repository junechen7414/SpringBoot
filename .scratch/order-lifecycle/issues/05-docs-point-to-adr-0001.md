# 文件：「全面軟刪除」的說法改成指向 ADR 0001

Status: resolved
Blocked by: 01, 02, 03

背景見 `../spec.md`、`docs/adr/0001-soft-delete-only-for-termination.md`。

## 要做的事

- `docs/agents/06-architecture.md` 的 Entity 層一節：補一句「軟刪除只用於終止動作（Deactivate／Delist／Cancel），Order Line 是實體刪除」，並連到 ADR 0001。
- `docs/handout/02-persistence-jpa.md`：§2（軟刪除）和 §4 第 2 點（「移除子實體保有逐筆樂觀鎖」那段的 `@SQLDelete` 寫法）改成新的設計。
- `筆記.md`：更新引用 `@SQLDelete`、`restore()` 和「全面軟刪除」的段落。
- 改動 `docs/agents/*` 之後，委派 `ai-doc-sync` 做 dry-run，確認其他 AI 工具的對應檔保持一致。

## 驗收

- 在 `docs/` 和 `筆記.md` 裡搜尋 `soft-delete-everywhere`、「全面軟刪」、`@SQLDelete`，不再有和 ADR 0001 矛盾的描述。

## Comments

- 2026-10-13：完成（純文件）。
  - `docs/agents/06-architecture.md` 的 Entity 層：第一條補上「軟刪除只用於終止動作，Order Line 是實體刪除」並連到 ADR 0001；`SoftDeleteMetadata` 那條註明只給有終止動作的 entity。
  - `docs/handout/02-persistence-jpa.md`：§2 開頭補上「先決定哪些 entity 要軟刪除」；§4 第 2 點改成 Hibernate 對帶 `@Version` 的 entity 預設的 `DELETE … WHERE ID = ? AND VERSION = ?`；「為什麼沒有成對的 `removeOrderLine()`」裡「軟刪後仍應保有 `ORDER_ID`」那條也改寫。注意：`docs/handout/` 被 `.gitignore` 排除，這份的修改只存在本機。
  - `筆記.md`：軟刪除策略那段補上適用範圍；「移除明細保有逐筆樂觀鎖」和 `removeOrderLine()` 兩段改成實體刪除的說法，舊的 `@SQLDelete` 寫法標明是以前的做法。
  - `ai-doc-sync` dry-run：入口檔都用 `@` 匯入，沒有不同步。照它的建議補了兩處：`new-domain-scaffold` skill（`.claude` 與 `.bob` 兩份）寫明「只有具備終止動作的 entity 才組合 `SoftDeleteMetadata`」；`01-overview.md` 架構特色第 2 點補上範圍。
  - 驗收搜尋：`docs/` 與 `筆記.md` 裡剩下的 `@SQLDelete` 只有兩種，一是「為何用顯式 UPDATE 而非 `@SQLDelete`」的通用比較，二是標明「以前」的歷史敘述，沒有和 ADR 0001 矛盾的描述。`soft-delete-everywhere`、「全面軟刪」已無命中（ADR 本身除外）。
