package com.ibm.demo.exception;

// 當找不到特定帳號時拋出
public class AccountNotFoundException extends ResourceNotFoundException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}