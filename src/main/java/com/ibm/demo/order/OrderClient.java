package com.ibm.demo.order;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.ibm.demo.order.DTO.internal.OrderExistenceResponse;

@HttpExchange("/order")
public interface OrderClient {

    /**
     * 查詢帳戶是否仍有有效訂單
     * 對應 /order/account/{accountId}/existence
     */
    @GetExchange("/account/{accountId}/existence")
    OrderExistenceResponse getOrderExistence(@PathVariable("accountId") Integer accountId);
}