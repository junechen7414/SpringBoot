# Product：`PUT` 拿掉 saleStatus，有 Reservation 時不能 Delist

Status: resolved（`98884d8`）

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

## 注意

- ⚠️ 下游 `order.spec.ts`「當商品狀態為無效時，無法建立新訂單」靠 `PUT /product` 帶 `saleStatus: Inactive`，做完之後會紅。見 `06-downstream-e2e-stop-using-put-status.md`。

## Comments

- 2026-10-10：直接在 `main` 實作（沒有 migration，屬低風險）。
  - `UpdateProductRequest` 只剩 `name`、`price`、`available`；`ProductService.updateProduct` 不再寫 saleStatus；移除 `Product.restore()`。
  - 新增 `ErrorCode.PRODUCT_STILL_HAS_RESERVATION_CAN_NOT_BE_DELETED`（400）。`deleteProduct` 在 `reserved > 0` 時拋出，不呼叫 `softDeleteById`。`ErrorCodeTest` 用 `@EnumSource` 自動涵蓋新常數，不需要改。
  - **驗收第 2 條原本的前提不成立**：`reserveProduct`／`releaseProduct` 是 JPQL bulk UPDATE，Hibernate 不會自動遞增 `@Version`。所以「檢查完、軟刪除前有並發 reserve」時，帶 version 的軟刪除仍然會命中，靜默成功。已改成兩條 UPDATE 都自己 `p.version = p.version + 1`，並補 `OptimisticLockingIntegrationTest.testSoftDeleteProductFailsAfterConcurrentReserve`。拿掉 version 加 1 時這支測試會紅，已確認。
    - 副作用（符合預期）：`PUT /product/{id}` 讀到商品之後如果有並發的 reserve／release，現在會回 409，不會再用舊的 `available` 蓋掉這次預留。
  - `ProductController` 的 `PUT`、`DELETE` 說明已改寫，`DELETE` 補上 400 回應。
  - `swagger.json`（`generateOpenApiDocs`）：`UpdateProductRequest` 已經沒有 `saleStatus`。
  - `./gradlew test -Djunit.platform.exclude.tags=SanityTest`：211 個測試全數通過。
