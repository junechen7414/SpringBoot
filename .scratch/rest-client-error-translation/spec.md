# 下游錯誤轉譯：解析寬鬆，語意嚴格

## 狀態與目的

本文件記錄已確認的改善方向。**已實作**（分支 `refactor/explicit-downstream-error-translation`），現況見 `docs/api-response-contract-decision.md` Phase 3。

實作 issue：[01 — 明確轉譯已知下游錯誤，停止猜測未知 400／404](issues/01-refine-downstream-error-translation.md)。

`RestClientErrorHandler` 是 outbound 的翻譯邊界：將下游 HTTP 錯誤回應轉成本地例外，再由 `GlobalExceptionHandler` 決定對外回應。它不是所有 outbound exception 的總處理器；連線失敗、逾時等沒有收到 HTTP response 的情況，不會進入目前的 status handler。

## 問題

目前實作有兩個方向相反的問題：

1. **太容易判定為業務拒絕**：HTTP 400／404 無法辨識 code 或 body 時，仍猜成 `INVALID_REQUEST`／`RESOURCE_NOT_FOUND`。但遠端 404 可能來自錯誤 URL，而非商品不存在。此分類不只影響對外歸責，也會因 `BusinessException` 被 CircuitBreaker 忽略而影響故障統計。
2. **已知資訊被壓平**：除了 400／404，其他錯誤一律轉成 `SystemException`，最後對外回 500。已知的 409 衝突、503 滿載／熔斷因此失去原本可表達的語意。

另外，目前只檢查遠端 code 對應某個 4xx，沒有核對它是否與真正的 HTTP status 一致。例如 HTTP 400 帶 `RESOURCE_NOT_FOUND`，可能被重建為對外 404。

## 決策

### 1. 只轉譯能確認且適合對外表達的語意

**解析寬鬆，語意嚴格**：保留 JSON tree，不要求整份 body 綁定固定 DTO；未知的額外欄位可以忽略，但不能據不完整的資訊猜測業務原因。

採用遠端語意須同時符合：

- 真正 HTTP status 與明確允許的 code 配對一致；不能只因 enum 存在且屬於 4xx 就接受。
- body 必須是可解析的 JSON object，且 code 為可辨識的字串。
- 不以 body.status 覆蓋 HTTP status。body.status 可缺席；若提供，必須是與真正 HTTP status 一致的整數。
- 不接受帶有 `errors` 屬性的回應作為下表的已知轉譯，包含 `errors: null`。

已確認配對時，detail 缺少或空白可使用本地 title／安全說明，不必因此轉成 500。正常 body 的未知額外欄位不影響分類。

### 2. 明確轉譯矩陣

| 下游真正 HTTP status 與 code | 對外結果 | detail 原則 |
|---|---|---|
| 400 ＋下列明確允許的具體業務碼 | 400，保留同 code | 保留已審視可公開的業務說明；缺少時用本地 title |
| 404 ＋ `RESOURCE_NOT_FOUND` | 404／`RESOURCE_NOT_FOUND` | 保留受控 loopback 的業務說明；缺少時用本地 title |
| 409 ＋ `OPTIMISTIC_LOCK_CONFLICT` | 409／同 code | 使用本地既有安全衝突說明 |
| 503 ＋ `BULKHEAD_FULL` | 503／同 code | 使用本地既有安全滿載說明 |
| 503 ＋ `CIRCUIT_OPEN` | 503／同 code | 使用本地既有安全暫時不可用說明 |
| 未知、無法解析、不支援或契約不一致的回應，包括 400／404 | 500／`INTERNAL_ERROR` | 固定安全說明，診斷資訊只進 log |
| 401／403／405／415／429 及其他 5xx | 維持 500／`INTERNAL_ERROR` | 不照抄成外部呼叫端的認證、協定或限流問題 |

400 允許清單：

- `ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED`
- `PRODUCT_ALREADY_EXIST`
- `PRODUCT_STILL_HAS_RESERVATION_CAN_NOT_BE_DELETED`
- `PRODUCT_STOCK_NOT_ENOUGH`

這是受控 loopback 的共通政策，不是第三方服務通用代理。未來新增可轉譯的 code，必須同步審視其對外語意、登錄政策並補測試，不能自動放行所有 4xx enum。

**不放行的情況：**

- 未知／缺少 code、空 body、非 object／無法解析的 JSON、code/status 矛盾：整合異常，不再靠 400／404 推導業務碼。
- 泛用 `INVALID_REQUEST`：不足以證明是外部操作的業務問題。目前 order 本地重複商品及 testdata 使用它的行為不受影響。
- `VALIDATION_FAILED` 或帶 `errors`：內部 request 可能由呼叫端服務自行組錯，不能直接歸咎外部輸入，也不能保證遠端欄位對應外部欄位。本次視為整合異常，不再降級為 `INVALID_REQUEST`，不轉送驗證陣列。

### 3. 分開決定公開回應與故障統計

- 保留 `BusinessException` 僅接受 4xx 的限制，不用它承載 503。
- 已確認業務拒絕與新增的 409 是預期結果，不計入 CircuitBreaker failure。
- 遠端 503、未知 400／404 與其他整合失敗計入 failure。改善對外 status 不代表將依賴故障改算成功或忽略。
- 現行 `application.yml` 的 CircuitBreaker `ignore-exceptions` 包含 `BusinessException`。實作須驗證實際計數，而不只是比較回應 status；不預設一定需要修改設定。
- 對外回應沿用 `GlobalExceptionHandler` 的共同出口，不另外建一套格式。新例外的命名與組織由實作者決定，不在此鎖死。

### 4. 公開與診斷資訊的界線

- 500 固定回 `INTERNAL_ERROR` 與安全 detail，不公開原始錯誤。
- 新增 409／503 使用本地安全說明，不直接複製下游 detail。
- 不轉送遠端 instance、errors 或任意 headers，包含 `Retry-After`。外層 instance 仍代表外部這次請求。
- 整合異常保留 `remoteStatus`、可辨識的 `remoteCode`、分類失敗原因等受控診斷資訊。必要遠端說明只留在受控 context，不將完整不可信 body 或敏感資料直接寫入 log／回應。

## 相容性與取捨

**這是有意變更既有 fallback，不是純內部重構。**

- 未知／無效的遠端 400、404，以及遠端驗證失敗，從一般業務 4xx 改成安全 500；對應舊測試需要改寫。
- 已知遠端 409／503 從 500 改回可確認的衝突／暫時不可用。
- 已知庫存不足等業務 400、資源 404，以及本地原生錯誤契約不應退化。

代價是：真正的業務錯誤若漏登錄，也會收到較籠統的 500。但目前下游是同一應用、同版本的 loopback，正常回應應符合共用契約。無法辨識時，明確暴露整合問題比靜默猜測更容易發現與修正。

本決策取代討論早期「保留未知 400／404 fallback」的選擇；不應把該選擇列為仍需相容的要求。

## 沿用的既有設計

參考 [API 回應契約決策](../../docs/api-response-contract-decision.md) 的「Phase 3 — `RestClientErrorHandler`」。

- 保留下游具體業務 code 的需求早已處理，本次不是首次補上 code。
- 保留 JSON tree；`DefaultResponseErrorHandler` 遷移繼續延後，不把解析機制替換綁進這張 issue。
- 保留 `BusinessException + ErrorCode` 與 RFC 9457／`application/problem+json`。
- 現有轉譯單元測試不足以單獨證明最終外部回應，需補串起 HTTP client 與 advice 的測試。

## 非目標

- 不刪除或重設計 RFC 9457 欄位，不新增成功信封。
- 不建立可配置的通用轉譯框架，不重做 `BusinessException`。
- 不新增自動 retry、idempotency、`Retry-After` 政策。
- 不修改訂單補償、交易邊界、DB migration、CI 或 auth 規則。
- 下游 429 與沒有收到 HTTP response 的連線／逾時細分留待後續，本張維持它們目前的公開結果。

## 實作與發布注意事項

這張 spec／issue 的建立不包含 Java 實作或發布。未來實作時：

- 依 `docs/agents/03-git-workflow.md` 評估跨 domain 錯誤契約變更的 branch＋PR 流程。
- 檢查下游 `../Playwright` 是否依賴改動的 status／code；有相依時記錄同步需求與發布順序，不假設上游測試全綠即代表下游相容。
- 同步既有錯誤契約決策與教學文件；不要現在先改成已完成。
