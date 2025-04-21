package com.ibm.demo.order.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    private int orderId;
    private int accountId;
    private int status;
    private BigDecimal totalAmount;
    private LocalDateTime createDate;
}
