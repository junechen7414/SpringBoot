package com.ibm.demo.exception.NotFound;

// 當找不到特定商品時拋出
public class ProductNotFoundException extends ResourceNotFoundException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}