package com.ibm.demo.order.DTO;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderRequest {
    private int orderId;
    private List<UpdateOrderDetailRequest> items;
}
