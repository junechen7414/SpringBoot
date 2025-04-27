package com.ibm.demo.order.DTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderDetailResponse {
    private Integer productId;
    private Integer quantity;
    private BigDecimal Price;
}
