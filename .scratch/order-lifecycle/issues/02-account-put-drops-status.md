# Account：`PUT` 拿掉 status，Deactivate 只走 `DELETE`

Status: ready-for-agent

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

## Comments
