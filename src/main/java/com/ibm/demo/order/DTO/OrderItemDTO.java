package com.ibm.demo.order.DTO;

import java.math.BigDecimal;

import lombok.Builder;

@Builder
public record OrderItemDTO(
        Integer productId,
        String productName,
        Integer quantity,
        BigDecimal productPrice) {
}