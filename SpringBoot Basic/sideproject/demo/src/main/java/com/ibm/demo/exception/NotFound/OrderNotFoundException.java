package com.ibm.demo.exception.NotFound;

// 當找不到特定訂單時拋出
public class OrderNotFoundException extends ResourceNotFoundException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}