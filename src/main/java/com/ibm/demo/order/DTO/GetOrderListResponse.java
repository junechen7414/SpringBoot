package com.ibm.demo.order.DTO;

import java.math.BigDecimal;

import lombok.Builder;

@Builder
public record GetOrderListResponse(
        Integer orderId,
        Integer status,
        BigDecimal totalAmount) {
}
