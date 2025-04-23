package com.ibm.demo.order.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderResponse {
    private int orderId;
    private BigDecimal totalAmount;
    private LocalDateTime modifiedDate;
    private List<UpdateOrderDetailResponse> items;
}
