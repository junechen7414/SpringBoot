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
public class GetOrderDetailResponse {
    private Integer accountId;
    private Integer orderStatus;
    private BigDecimal totalAmount;
    private LocalDate createDate;
    private LocalDate modifiedDate;
    private List<OrderItemDTO> items;

}
