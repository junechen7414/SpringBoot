# 訂購系統（Ordering）

Account 向系統下 Order 購買 Product；下單時會為每個 Product 保留庫存，直到 Order 被取消為止。

## Language

### 參與者

**Account**:
下 Order 的一方，也就是買方。
_Avoid_: User、Customer、會員

**Deactivate**:
Account 的唯一終止動作；停用後的 Account 對系統而言不再存在，且不可恢復。還有 Active Order 的 Account 不能停用。
_Avoid_: 刪除帳戶、Delete

**API Credential**:
呼叫本系統 API 時用來認證的機器帳號，與 Account 完全無關，兩者不合併。
_Avoid_: account、使用者帳號

### 訂單

**Order**:
Account 一次購買一或多個 Product 的紀錄。
_Avoid_: OrderInfo、訂單主檔

**Order Line**:
Order 中針對單一 Product 的一行，記錄購買數量；一張 Order 裡同一個 Product 只會有一行 Order Line。
_Avoid_: OrderDetail、OrderItem、明細

**Active Order**:
尚未被 Cancel 的 Order。
_Avoid_: Created Order、有效訂單、未刪除訂單

**Cancel**:
Order 的唯一終止動作：釋放這張 Order 的 Reservation；取消後的 Order 對系統而言不再存在，且不可恢復。
_Avoid_: 刪除訂單、Delete

### 商品與庫存

**Product**:
可以被 Order 購買的商品。
_Avoid_: Item、商品主檔

**Delist**:
Product 的唯一終止動作；下架後的 Product 對系統而言不再存在，且不可恢復，要重新販售就建立新的 Product。還有 Reservation 的 Product 不能下架。
_Avoid_: 刪除商品、Delete、停售

**Reservation**:
下單時，從 Product 的可售庫存保留給某張 Order 的數量，在 Order 被 Cancel 時歸還。本系統沒有出貨或結單（Fulfillment），因此 Reservation 不會轉成「已售出」。
_Avoid_: 扣庫存、預扣

## 關係

- Account、Product、Order 各有唯一一個終止動作（Deactivate、Delist、Cancel），終止後都從系統消失、不可恢復。
- 終止有先後限制：還有 Active Order 的 Account 不能 Deactivate，還有 Reservation 的 Product 不能 Delist。
- Order Line 沒有自己的終止動作，它只是 Order 的組成部分：Order 更新時移除的 Order Line 就不存在了，不留紀錄。
