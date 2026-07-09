package com.ibm.demo.order.DTO;

import java.util.Set;

import com.ibm.demo.product.DTO.internal.OrderItemRequest;

/**
 * 訂單刪除流程的「交易內快照」。
 * <p>
 * 在 session 仍開啟的 readOnly 交易內，一次把刪除流程後續步驟(遠端釋放庫存、
 * 樂觀鎖軟刪、失敗補償與告警)所需的資料萃取為純資料，避免 lazy 關聯
 * ({@code OrderInfo.orderDetails})洩漏到交易/session 之外而依賴 OSIV。
 */
public record OrderDeletionPlan(
        Integer accountId,
        Integer version,
        Set<OrderItemRequest> items) {
}
