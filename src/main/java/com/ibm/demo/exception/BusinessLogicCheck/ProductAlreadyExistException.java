package com.ibm.demo.exception.BusinessLogicCheck;

public class ProductAlreadyExistException extends BusinessException{
    public ProductAlreadyExistException(String message) {
        super(message);
    }
}
