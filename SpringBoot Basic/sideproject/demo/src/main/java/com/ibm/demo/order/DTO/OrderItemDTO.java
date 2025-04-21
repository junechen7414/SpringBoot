package com.ibm.demo.order.DTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {
    private int productId;
    private String productName;
    private int quantity;
    private BigDecimal productPrice;
}
