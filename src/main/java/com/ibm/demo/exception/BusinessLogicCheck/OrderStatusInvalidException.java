package com.ibm.demo.exception.BusinessLogicCheck;

import com.ibm.demo.util.ErrorCode;

public class OrderStatusInvalidException extends BusinessException{
    public OrderStatusInvalidException(String message) {
        super(ErrorCode.ORDER_STATUS_INVALID, message);
    }
}
