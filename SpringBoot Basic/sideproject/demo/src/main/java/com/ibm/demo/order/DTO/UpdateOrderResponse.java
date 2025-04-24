package com.ibm.demo.order.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderResponse {
    private Integer orderId;
    private BigDecimal totalAmount;
    private LocalDate modifiedDate;
    private List<UpdateOrderDetailResponse> items;
}
