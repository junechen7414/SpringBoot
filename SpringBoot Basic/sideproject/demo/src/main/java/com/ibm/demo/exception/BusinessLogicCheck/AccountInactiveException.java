package com.ibm.demo.exception.BusinessLogicCheck;

public class AccountInactiveException extends BusinessException{
    public AccountInactiveException(String message) {
        super(message);
    }    
}
