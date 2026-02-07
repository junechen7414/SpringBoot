package com.ibm.demo.exception.BusinessLogicCheck;

public class OrderStatusInvalidException extends BusinessException{
    public OrderStatusInvalidException(String message) {
        super(message);
    }
}
