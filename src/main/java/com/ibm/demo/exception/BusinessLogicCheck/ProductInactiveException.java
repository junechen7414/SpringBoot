package com.ibm.demo.exception.BusinessLogicCheck;

import com.ibm.demo.util.ErrorCode;

public class ProductInactiveException extends BusinessException{
    public ProductInactiveException(String message) {
        super(ErrorCode.PRODUCT_INACTIVE, message);
    }
}
