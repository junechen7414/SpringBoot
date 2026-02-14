package com.ibm.demo.product.DTO;

import java.math.BigDecimal;

import lombok.Builder;

@Builder
public record GetProductDetailResponse(
                String name,
                BigDecimal price,
                Integer saleStatus,
                Integer stockQty) {
}