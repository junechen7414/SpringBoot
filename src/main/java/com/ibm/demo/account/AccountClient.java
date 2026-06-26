package com.ibm.demo.account;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.ibm.demo.account.DTO.GetAccountDetailResponse;

@HttpExchange("/account")
public interface AccountClient {
    @GetExchange("/{id}")
    GetAccountDetailResponse getAccountDetail(@PathVariable("id") Integer accountId);

    /**
     * 驗證帳戶是否具下單資格（存在且為啟用狀態）。
     * 不符資格時由帳戶端拋出例外，呼叫端無須知道帳戶的狀態規則。
     */
    @GetExchange("/{id}/order-eligibility")
    void assertCanPlaceOrder(@PathVariable("id") Integer accountId);
}
