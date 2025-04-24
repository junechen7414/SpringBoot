package com.ibm.demo.order.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    private Integer orderId;
    private Integer accountId;
    private Integer status;
    private BigDecimal totalAmount;
    private LocalDate createDate;
}
