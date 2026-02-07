package com.ibm.demo.exception.BusinessLogicCheck;

public class AccountStillHasOrderCanNotBeDeleteException extends BusinessException {
    public AccountStillHasOrderCanNotBeDeleteException(String message) {
        super(message);
    }
    
}
