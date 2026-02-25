package com.ibm.demo.exception.BusinessLogicCheck;

import com.ibm.demo.util.ErrorCode;

public class AccountStillHasOrderCanNotBeDeleteException extends BusinessException {
    public AccountStillHasOrderCanNotBeDeleteException(String message) {
        super(ErrorCode.ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED, message);
    }
    
}
