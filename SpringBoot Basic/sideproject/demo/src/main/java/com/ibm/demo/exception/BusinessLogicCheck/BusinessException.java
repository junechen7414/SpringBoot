package com.ibm.demo.exception.BusinessLogicCheck;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public abstract class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
