package com.ibm.demo.exception.BusinessLogicCheck;

import com.ibm.demo.util.ErrorCode;

public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }

}
