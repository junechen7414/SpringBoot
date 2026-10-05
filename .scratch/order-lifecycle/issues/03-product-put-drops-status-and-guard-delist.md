# Product：`PUT` 拿掉 saleStatus，有 Reservation 時不能 Delist

Status: ready-for-agent

背景與決策見 `../spec.md`。

## 要做的事

- `UpdateProductRequest` 拿掉 `saleStatus`；`ProductService.updateProduct` 不再呼叫 `setSaleStatus`。
- `ProductService.deleteProduct`（Delist）：Product 的保留量（`reserved`）大於 0 時拒絕，拋出 `BusinessException`。新增一個 `ErrorCode`，命名請比照 `ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED` 的寫法，`ErrorCodeTest` 要一起更新。這個檢查只看 Product 自己的庫存數字，**不要**引入對 order 的依賴。
- 移除 `Product.restore()`。
- 更新 `ProductController` 上 `PUT` 和 `DELETE` 的 OpenAPI 說明。

## 為何要擋

如果 Product 在還有 Reservation 時就被 Delist，之後那張 Order 被 Cancel 時，Reservation 就要歸還給一個「已經不存在」的 Product。這條限制和「還有 Active Order 的 Account 不能 Deactivate」是對稱的。

## 驗收

- 保留量大於 0 時 `DELETE /product/{id}` 回 400，並帶上新的 `code`；保留量等於 0 時回 204。
- 檢查和軟刪除之間如果有並發的 reserve：軟刪除的 UPDATE 有帶 version，這種情況會變成樂觀鎖衝突（409），不會靜默成功。請補一個測試確認。
- `PUT /product/{id}` 的 body 不再有 `saleStatus`；`swagger.json` 已更新。
- `./gradlew test` 全部通過。

## Comments
