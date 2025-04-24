package com.ibm.demo.product.DTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetProductListResponse {
    private Integer id;
    private String name;
    private BigDecimal price;
    private Integer saleStatus;
    private Integer stockQty;
    
}
