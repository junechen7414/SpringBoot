# 下游 E2E：停止用 `PUT` 改 status，改走 `DELETE`

Status: resolved（下游 `3d1ab02`）
Blocked by: —（01 已經讓下游變紅，02、03 push 之後會再多紅幾條）

背景與決策見 `../spec.md`。這張要改的是**下游 repo `junechen7414/Playwright-TS`**（本機 clone：`../Playwright`），不是本 repo。

## 為什麼會壞

01／02／03 把 `status` 從三個 `PUT` 的 body 拿掉。專案沒有覆寫 `FAIL_ON_UNKNOWN_PROPERTIES`，所以帶舊欄位的請求**不會 400**，照樣回 204；但 status **不會再被套用**。下游有幾條測試是「先用 `PUT` 把資源改成停用／取消，再驗證後續行為」，前置步驟靜悄悄地沒生效，後面的斷言就失敗了。

01–03 的「注意」只確認了「未知欄位會被忽略」，沒有檢查下游是不是**依賴 status 被套用**，所以漏掉了。

## 受影響的測試（`origin/main` @ `a1f2c13`）

| 檔案 | 測試 | 依賴 | 上游 issue | 狀態 |
|---|---|---|---|---|
| `tests/api/springboot/order.spec.ts` | 當訂單狀態不再是 1001 時，應回報沒有有效訂單 | `PUT /order` 帶 `orderStatus: Cancelled` | 01 | **已經紅**（下游 run #219、#220） |
| `tests/api/springboot/account.spec.ts` | 當帳戶有關聯訂單時，無法將狀態改為停用 | `PUT /account` 帶 `status: Inactive`，期望 400 | 02 | push 02 後會紅 |
| `tests/api/springboot/account.spec.ts` | 當帳戶狀態為停用時，不具下單資格 | 同上，期望 204 後 404 | 02 | push 02 後會紅 |
| `tests/api/springboot/order.spec.ts` | 當帳戶狀態為無效時，無法建立新訂單 | 同上 | 02 | push 02 後會紅 |
| `tests/api/springboot/order.spec.ts` | 當商品狀態為無效時，無法建立新訂單 | `PUT /product` 帶 `saleStatus: Inactive` | 03 | 做完 03 後會紅 |

上表是 grep 出來的，CI log 要登入才看得到，沒辦法逐條核對。修完後要以下游 CI 實際跑出的結果為準。

## 要做的事

- 「停用／取消」的前置步驟改成呼叫 `DELETE /account/{id}`、`DELETE /order/{id}`、`DELETE /product/{id}`。
- 「有關聯訂單時無法將狀態改為停用」：這個行為已經不存在，刪掉這條測試。「有關聯訂單時無法刪除」已經涵蓋同一條規則。
- fixture 裡的 `updateOrderData`（`orderStatus`）、`updateAccountData`（`status`）、`updateProductData`（`saleStatus`）把 status 拿掉。
- `services/schema/api-types.ts` 會隨 swagger.json 重新產生，確認型別裡已經沒有 status 欄位。

## 順序

在 02（以及之後的 03）push 之前，先把下游改好，或至少同一天內補上，免得下游一直紅著（`03-git-workflow.md`：「main 紅了」）。

## Comments

- 2026-10-12：下游已在 `3d1ab02` 對齊（`junechen7414/Playwright-TS`）。上游 `0bd9909` 的 image 發布之後才 push，下游 CI 全綠：Spring Boot API E2E Tests（run 38206554508）、Saucedemo UI Tests（run 38206554526）。
  - `account.spec`：刪掉「有關聯訂單時無法將狀態改為停用」。「當帳戶狀態為停用時，不具下單資格」改走 `DELETE` 之後，跟既有的「當帳戶已被刪除時，不具下單資格」完全一樣，所以也一起刪掉。
  - `order.spec`：兩個建單的前置步驟改成 `DELETE /account`、`DELETE /product`，商品用沒有保留量的那筆。「訂單狀態不再是 1001」已由「訂單被刪除後」涵蓋，刪掉。
  - `product.spec`：新增兩條，一條是有保留量時 `DELETE` 回 400 `PRODUCT_STILL_HAS_RESERVATION_CAN_NOT_BE_DELETED`，另一條是先取消訂單釋放保留量後 `DELETE` 回 204、之後 `GET` 回 404。
  - fixture／client 拿掉 `status`、`saleStatus`、`orderStatus`；`api-types.ts` 已重新產生。
  - 這次的 push 順序要記住：下游 CI 一律拉 ghcr 的 `latest`（`pull_policy: always`）。上游的 image 發布之前，下游如果先 push，少了必填狀態欄位的 PUT 會被舊 image 擋成 400。另外，`generate-docs` 會往下游 push `swagger.json`，所以下游 push 前要先 rebase。
