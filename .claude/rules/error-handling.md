---
paths:
  - "src/main/java/com/ibm/demo/GlobalExceptionHandler.java"
  - "src/main/java/com/ibm/demo/exception/**"
---

# 錯誤處理：GlobalExceptionHandler 內部細節

總則（拋哪種例外、RFC 9457、以 `code` 分流）見 `docs/agents/06-architecture.md`「Service 層」。以下是修改 handler／例外型別時才需要的細節。

- `code` 值為 `ErrorCode` 的常數名 —— 但框架自己攔下的協定層錯誤（405、415…）例外，那些的 `code` 由 HTTP 狀態名推導（`METHOD_NOT_ALLOWED`…）。驗證失敗另帶 `errors` 陣列。
- `exception/ApiErrorResponse` 只是給 springdoc 看的 schema 宣告，**不參與執行期序列化**；RFC 9457 六個欄位標 `required`，但 `code` **刻意不列 enum**（值域 50+ 個，列出來沒人會看，見該處註解）。
- handler 繼承 `ResponseEntityExceptionHandler`（框架自己拋的那批例外它已處理好），我們只覆寫兩個驗證 `handleXxx`、為自訂例外加 `@ExceptionHandler`，並覆寫 `handleExceptionInternal(...)` 做共同處理 —— 補 `code`／`type`、記唯一那行 log，**等級由最終 HTTP status 決定**（500 → ERROR 帶 stack trace，其餘 → WARN）。
- 自訂 handler 也把 body 交給 `handleExceptionInternal(...)`，不自己 `new ResponseEntity`。
