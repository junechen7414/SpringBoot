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
public class GetOrderDetailResponse {
    private int accountId;
    private int orderStatus;
    private BigDecimal totalAmount;
    private LocalDateTime createDate;
    private LocalDateTime modifiedDate;
    private List<OrderItemDTO> items;

}
