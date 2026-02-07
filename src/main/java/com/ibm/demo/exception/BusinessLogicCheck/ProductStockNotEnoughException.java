package com.ibm.demo.exception.BusinessLogicCheck;

public class ProductStockNotEnoughException extends BusinessException{
    public ProductStockNotEnoughException(String message) {
        super(message);
    }
}
