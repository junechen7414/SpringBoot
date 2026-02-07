package com.ibm.demo.exception.BusinessLogicCheck;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AccountInactiveException extends BusinessException{
    public AccountInactiveException(String message) {
        super(message);
    }    
}
