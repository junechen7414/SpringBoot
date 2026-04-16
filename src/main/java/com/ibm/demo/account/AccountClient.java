package com.ibm.demo.account;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.ibm.demo.account.DTO.GetAccountDetailResponse;

@HttpExchange("/account")
public interface AccountClient {
    @GetExchange("/{id}")
    GetAccountDetailResponse getAccountDetail(@PathVariable("id") Integer accountId);
}
