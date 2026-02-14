package com.ibm.demo.order.DTO;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;

@Builder
public record GetOrderDetailResponse(
        Integer accountId,
        Integer orderStatus,
        BigDecimal totalAmount,
        List<OrderItemDTO> items) {
}