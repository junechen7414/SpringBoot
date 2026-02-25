package com.ibm.demo.exception.BusinessLogicCheck;

import com.ibm.demo.util.ErrorCode;

public class ProductStockNotEnoughException extends BusinessException {
    public ProductStockNotEnoughException(String message) {
        super(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH, message);
    }
}
