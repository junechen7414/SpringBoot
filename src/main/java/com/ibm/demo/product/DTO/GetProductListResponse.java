package com.ibm.demo.product.DTO;

import java.math.BigDecimal;

public record GetProductListResponse(
                Integer id,
                String name,
                BigDecimal price,
                Integer saleStatus,
                Integer stockQty) {
}