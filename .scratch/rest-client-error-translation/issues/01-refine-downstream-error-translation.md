# 01 — 明確轉譯已知下游錯誤，停止猜測未知 400／404

Status: resolved

Spec：[../spec.md](../spec.md)

本 issue 已實作，以下為當初的開工指引。開工前先讀 `docs/agents/03-git-workflow.md`、`.claude/rules/error-handling.md`、`.claude/rules/security.md` 與 spec；依跨 domain 錯誤契約變更評估 branch＋PR 流程。若涉及 resilience 設定、環境或測試啟動，先讀對應專案指引。

## 背景

`RestClientErrorHandler` 現在把 400／404 視為業務拒絕，無法辨識時仍按 status 給預設業務碼；其他錯誤一律轉成系統失敗。這同時造成：

- 不明的 400／404 被猜成外部呼叫端的問題，並可能被 CircuitBreaker 當作正常業務拒絕忽略。
- 已知的 409 衝突與 503 滿載／熔斷失去語意，最終都變成 500。
- 只驗證遠端 code 為 4xx，沒有核對其與真正 HTTP status 是否一致，可能讓 body code 改變最終 status。

改善原則：**解析寬鬆，語意嚴格；只轉譯能確認且適合對外表達的原因，未知的當整合異常，不猜。**

## 實作範圍

### 1. 明確分類下游回應

以 spec 的「明確轉譯矩陣」為唯一行為規格：

- 只接受真正 HTTP status 與明確允許 code 的配對；不自動放行所有 4xx enum。
- 支援既有具體業務 400、資源 404、新增的 409 與兩種 503。
- 未知／缺 code、空／不可解析／非 object body、矛盾 status 或不支援的契約，均轉成整合異常，對外安全 500。
- 泛用 `INVALID_REQUEST` 不足以確認遠端業務原因；`VALIDATION_FAILED`／`errors` 不再降級成一般業務拒絕。
- body.status 可缺席；若提供必須為與 HTTP status 一致的整數。已知配對缺少／空白 detail 時使用本地說明，未知額外欄位可忽略。

### 2. 保持例外與回應責任界線

- `RestClientErrorHandler` 負責 HTTP error → 本地例外；`GlobalExceptionHandler` 負責本地例外 → 對外 response。
- 重用共同回應組裝出口，不增加另一套 JSON 格式。
- 保留 `BusinessException` 的 4xx invariant；503 不得塞入此型別。
- 新 exception 類名與具體組織方式由實作者決定，不為矩陣新增通用可配置框架。
- 新增 409／503 使用本地安全 detail；不轉送遠端 instance、errors 或任意 headers。
- 整合異常留下受控診斷資訊，例如 remote status／code 與分類失敗原因；不直接記錄完整不可信 body 或敏感資料。

### 3. 驗證 CircuitBreaker 分類效果

現行 `application.yml` 忽略 `BusinessException`，因此翻譯結果會影響故障統計：

- 已確認業務拒絕與 409：不計入 failure。
- 遠端 503、未知 400／404 與其他整合失敗：計入 failure。

不能只為了讓 response 變成 503，就把該失敗改成被忽略。優先沿用既有設定；是否需改配置由實作驗證決定。

### 4. 測試、文件與下游檢查

- 更新原本保證「未知 400／404 仍 fallback」及「遠端驗證降級」的測試：這些是有意變更，不應維持舊斷言。
- 補轉譯矩陣的正向、負向及邊界案例。
- 使用可控 HTTP stub／test fixture 串起 configured RestClient、轉譯 handler 與 advice 的最終 HTTP 回應，隔離 Oracle；不只 mock Client 直接拋出預設例外。
- 實測受保護呼叫的 CircuitBreaker 計數，不只測 exception 型別或 HTTP status。
- 檢查下游 `../Playwright` 對 status／code 的依賴，記錄同步需求及發布順序。
- 實作完成時更新 `docs/api-response-contract-decision.md`、`docs/handout/03-exception-handling.md` 對應說明；`筆記.md` 若有具體受影響片段亦同步。不將待實作規格提前寫成現況。

## 參考檔案與可重用設計

| 檔案 | 用途 |
|---|---|
| `src/main/java/com/ibm/demo/RestClientErrorHandler.java` | 重用 JSON tree 解析／欄位讀取；替換 status 推導業務碼的 fallback 與分類規則 |
| `src/main/java/com/ibm/demo/GlobalExceptionHandler.java` | 重用 `respond`／`problem` 共用出口，承接依賴暫時不可用表示 |
| `src/main/java/com/ibm/demo/exception/BusinessException.java` | 保留僅接受 4xx 的 invariant |
| `src/main/java/com/ibm/demo/exception/SystemException.java` | 沿用受控診斷 context 的概念 |
| `src/main/java/com/ibm/demo/exception/ErrorCode.java` | 重用既有業務、衝突、滿載與熔斷代碼／status |
| `src/main/java/com/ibm/demo/config/RestClientConfig.java` | 保留 internal group 與 status handler 接線 |
| `src/main/resources/application.yml` | 核對 CircuitBreaker 忽略規則與實際分類效果，不預設必須修改 |
| `src/test/java/com/ibm/demo/RestClientErrorHandlerTest.java` | 更新現有 fallback 測試並擴充轉譯矩陣 |
| `src/test/java/com/ibm/demo/contract/ApiErrorContractTest.java` | 核對最終 RFC 9457 wire format 及本地錯誤不退化 |
| `docs/api-response-contract-decision.md` | 沿用 Phase 3 的 JSON tree 與延後 `DefaultResponseErrorHandler` 決策，更新已變更的 fallback 政策 |

## 驗收條件

- [ ] Spec 列出的具體業務 400／資源 404 保留正確 code；新增 `409 + OPTIMISTIC_LOCK_CONFLICT`、`503 + BULKHEAD_FULL`、`503 + CIRCUIT_OPEN` 正確轉譯，不再被壓成 500。
- [ ] 未知／缺 code、空／不可解析／非 object JSON、code/status 不符、泛用 `INVALID_REQUEST`、遠端驗證回應均回 `500 / INTERNAL_ERROR`，不再按 400／404 猜業務原因。
- [ ] `errors` 屬性存在時，包括 `errors: null`，不被當作可轉譯的已知回應。
- [ ] 已知配對缺少／空白 detail 可用本地安全說明；未知額外欄位不影響分類。body.status 若存在且型別錯誤或與真正 HTTP status 矛盾，不能接受。
- [ ] 已明確替換舊 fallback 與遠端驗證降級斷言；保留所有仍適用的既有測試，並補齊矩陣正反案例。
- [ ] 最終 HTTP status、body.status、code/type 一致，Content-Type 仍為 `application/problem+json`；500 不洩漏原始 detail，409／503 不轉送遠端說明，也不洩漏遠端 instance、errors、headers。
- [ ] 可控 HTTP stub／fixture 確實經過 configured RestClient → 轉譯 → advice → 最終回應，至少覆蓋已知 400、新 409、新 503、未知 404 → 500；不只 mock Client 拋例外，不要求重建完整 Oracle loopback 基底。
- [ ] 受保護呼叫的 CircuitBreaker failure 計數符合 spec：業務／409 不計，遠端 503、未知 400／404 及整合異常計入。
- [ ] 下游 401／403／405／415／429 與其他未支援失敗仍為安全 500。本地原生 400／404／409／429／503 與認證回應不退化；`BusinessException` 仍拒絕非 4xx。
- [ ] 已檢查下游 Playwright 的 status／code 依賴，並記錄是否需要同步測試及發布順序。
- [ ] 實作與既有錯誤契約／教學文件一致；相關本地筆記片段如有受影響亦已同步。
- [ ] 針對性測試與 CI gate `./gradlew test "-Djunit.platform.exclude.tags=SanityTest"` 通過；若有失敗或環境阻礙，記錄實際結果，不宣稱已驗證。

## 非目標

- 不改 RFC 9457 欄位、成功回應、`BusinessException` 骨架或 JSON tree 解析策略。
- 不遷移 `DefaultResponseErrorHandler`，不建立通用可配置轉譯框架。
- 不加入 retry、idempotency、`Retry-After` 政策，不改訂單補償或交易邊界。
- 不改 DB migration、CI 或 auth 規則。
- 下游 429 與無 HTTP response 的網路／逾時細分另議，維持目前公開結果。

## Comments

- 2026-10-18：建立待實作 issue。討論後重新評估早期相容性選擇，改採「未知或矛盾的 400／404 視為整合異常，不猜業務原因」；本次只建立 spec 與 issue，未實作、未執行測試。
