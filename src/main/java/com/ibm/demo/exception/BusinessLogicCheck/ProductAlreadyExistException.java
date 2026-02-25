package com.ibm.demo.exception.BusinessLogicCheck;

import com.ibm.demo.util.ErrorCode;

public class ProductAlreadyExistException extends BusinessException{
    public ProductAlreadyExistException(String message) {
        super(ErrorCode.PRODUCT_ALREADY_EXIST, message);
    }
}
