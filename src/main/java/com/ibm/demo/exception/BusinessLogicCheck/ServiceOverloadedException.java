package com.ibm.demo.exception.BusinessLogicCheck;

import com.ibm.demo.util.ErrorCode;

public class ServiceOverloadedException extends BusinessException {
    public ServiceOverloadedException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}