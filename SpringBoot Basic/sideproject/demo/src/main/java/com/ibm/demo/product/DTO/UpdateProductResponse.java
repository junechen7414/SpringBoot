package com.ibm.demo.product.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductResponse {
    private int id;
    private String name;
    private BigDecimal price;
    private int saleStatus;
    private int stockQty;
    LocalDate createDate;
    LocalDate modifiedDate;
}
