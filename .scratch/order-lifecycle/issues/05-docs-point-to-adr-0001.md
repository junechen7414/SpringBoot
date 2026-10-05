# 文件：「全面軟刪除」的說法改成指向 ADR 0001

Status: ready-for-agent
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
