package com.ibm.demo.product.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetProductDetailResponse {
    private String name;
    private BigDecimal price;
    private Integer saleStatus;
    private Integer stockQty;
    LocalDate createDate;
    LocalDate modifiedDate;

}
