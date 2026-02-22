package com.ibm.demo.order;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/order")
public interface OrderClient {

    /**
     * 開放查看帳戶ID是否存在任何訂單中的端點
     * 對應 /order/AccountIdIsInOrder/{accountId}
     */
    @GetExchange("/AccountIdIsInOrder/{accountId}")
    Boolean accountIdIsInOrder(@PathVariable("accountId") Integer accountId);
}