package com.ibm.demo;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.BusinessLogicCheck.BusinessException;
import com.ibm.demo.util.ErrorCode;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 優化：利用 BusinessException 帶出來的狀態碼動態處理
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = (errorCode != null) ? errorCode.getStatus() : HttpStatus.BAD_REQUEST;
        String errorType = (errorCode != null) ? errorCode.getMessage() : "Business Error";

        return createResponseEntity(status, errorType, ex.getMessage());
    }

    /**
     * 優化：使用 Stream API 提升可讀性處理參數校驗
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String detailedMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> String.format("[%s: %s]", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));

        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error("Validation Error")
                .message("參數驗證失敗: " + detailedMessage)
                .build();

        return new ResponseEntity<>(response, status);
    }

    private ResponseEntity<ApiErrorResponse> createResponseEntity(HttpStatus status, String errorType, String message) {
        ApiErrorResponse apiErrorResponse = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(errorType)
                .message(message)
                .build();
        return new ResponseEntity<>(apiErrorResponse, status);
    }
}