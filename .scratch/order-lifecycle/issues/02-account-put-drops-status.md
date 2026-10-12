# Account：`PUT` 拿掉 status，Deactivate 只走 `DELETE`

Status: resolved（`7976925`）

背景與決策見 `../spec.md`。

## 要做的事

- `UpdateAccountRequest` 拿掉 `status`；`AccountService.updateAccount` 不再呼叫 `setStatus`。
- `AccountLifecycleService.updateAccount` 裡「狀態由啟用轉為停用時先檢查 Active Order」的分支已經不會再發生，要移除。移除之後如果 `updateAccount` 只剩單純的委派，就評估 `PUT /account/{id}` 要不要改回由 `AccountController` 直接承接。判準見 `docs/agents/06-architecture.md` 的 Orchestration 一節；如果搬回去，`contract/ApiSuccessContractTest` 的 `@Import` 要一起更新。
- `DELETE /account/{id}`（Deactivate）的行為不變：還有 Active Order 時拒絕，否則軟刪除並設成 `N`。
- 移除 `Account.restore()`。
- 更新 `AccountLifecycleController` 上 `PUT` 的 OpenAPI 說明，裡面目前還寫著「若欲將狀態從啟用 'Y' 變更為停用 'N'…」。

## 驗收

- `PUT /account/{id}` 的 body 不再有 `status`；`swagger.json` 已更新。
- `AccountLifecycleServiceTest`、`AccountServiceTest` 已依照新行為調整。
- `./gradlew test` 全部通過。

## 注意

- 下游 E2E 如果在 `PUT /account` 的 body 裡帶了 `status`，一樣要確認未知欄位會被忽略（見 01 的「注意」）。
- ⚠️ 下游有 3 條測試靠 `PUT /account` 帶 `status: Inactive` 來停用帳戶，push 之後會紅。push 前先處理 `06-downstream-e2e-stop-using-put-status.md`。

## Comments

- 2026-10-06：直接在 `main` 實作（沒有 migration，屬低風險）。
  - `UpdateAccountRequest` 只剩 `name`；`AccountService.updateAccount` 不再寫 status；移除 `Account.restore()`。
  - 拿掉 status 之後，`AccountLifecycleService.updateAccount` 只剩委派、不再碰 order，依 06 的判準**把 `PUT /account/{id}` 搬回 `AccountController`**；orchestration 只留下 `DELETE`（Deactivate），行為不變。`ApiSuccessContractTest` 的 `@Import` 仍然需要兩個 controller（`DELETE` 還在 `AccountLifecycleController`），只更新了註解和 PUT 的 body。
  - `06-architecture.md` 的 Orchestration 一節同步改成「只承接 `DELETE`」。
  - `swagger.json`（`generateOpenApiDocs`）：`UpdateAccountRequest` 只剩 `name`，PUT 的說明已改寫。
  - 專案沒有覆寫 `FAIL_ON_UNKNOWN_PROPERTIES`，帶舊 `status` 欄位的呼叫端不會 400，但 status 不再生效。
  - `./gradlew test -Djunit.platform.exclude.tags=SanityTest`：204 個測試全數通過。
  - 沒有處理：`account-write-with-validation` 這個 bulkhead 名稱已經名不副實（`updateAccount`／`deleteAccount` 都不再發 HTTP），改名會牽動 Resilience4j 設定，留待之後再處理。
