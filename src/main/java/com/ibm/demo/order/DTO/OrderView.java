package com.ibm.demo.order.DTO;

import java.util.List;

import com.ibm.demo.product.DTO.internal.OrderItemRequest;

/**
 * 訂單的「交易內唯讀快照」。
 * <p>
 * 在 session 仍開啟的 readOnly 交易內，把讀取端點(明細、列表)後續組裝所需的訂單資料
 * ——含 lazy 的 {@code orderDetails}——一次萃取為純資料，避免關聯洩漏到交易/session 之外
 * 而依賴 OSIV。line item 沿用 {@link OrderItemRequest}(productId + quantity)，與
 * {@code OrderDeletionPlan} 一致。商品名稱/價格等需遠端取得的資料，由呼叫端在交易外補齊。
 */
public record OrderView(
        Integer orderId,
        Integer accountId,
        Integer status,
        List<OrderItemRequest> items) {
}
