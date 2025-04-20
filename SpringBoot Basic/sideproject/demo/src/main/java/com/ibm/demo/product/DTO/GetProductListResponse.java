package com.ibm.demo.product.DTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetProductListResponse {
    private int id;
    private String name;
    private BigDecimal price;
    private int saleStatus;
    private int stockQty;
    
}
