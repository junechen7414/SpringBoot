package com.ibm.demo.exception.BusinessLogicCheck;

import com.ibm.demo.util.ErrorCode;

public class AccountInactiveException extends BusinessException{
    public AccountInactiveException(String message) {
        super(ErrorCode.ACCOUNT_INACTIVE, message);
    }    
}
