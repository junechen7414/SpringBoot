package com.ibm.demo.product;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductListResponseDTO {
    private int id;
    private String name;
    private BigDecimal price;

}
