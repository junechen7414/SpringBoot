package com.ibm.demo.product.DTO;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "商品列表回應")
public record GetProductListResponse(
        @Schema(description = "商品 ID", example = "1")
        Integer id,

        @Schema(description = "商品名稱", example = "商品A")
        String name,

        @Schema(description = "商品價格", example = "250.00")
        BigDecimal price,

        @Schema(description = "銷售狀態 (1001=可銷售, 1002=不可銷售)", example = "1001")
        Integer saleStatus,

        @Schema(description = "可用庫存數量", example = "100")
        Integer available) {
}
