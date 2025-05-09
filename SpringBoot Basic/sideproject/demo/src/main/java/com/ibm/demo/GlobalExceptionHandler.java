package com.ibm.demo;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode; // 新增 import
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.BusinessException;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

        // 處理 ResourceNotFoundException
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
                        ResourceNotFoundException ex, WebRequest request) {

                ApiErrorResponse apiErrorResponse = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.NOT_FOUND.value(),
                                "Not Found",
                                ex.getMessage()
                // ,request.getDescription(false) //參數includeClientInfo false表示不包含客戶端資訊session
                // id 和 username
                // .replace("uri=", "")
                );

                // 找不到資源時，回傳 404 NOT FOUND
                return new ResponseEntity<>(apiErrorResponse, HttpStatus.NOT_FOUND);
        }

        // 處理 BusinessException
        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ApiErrorResponse> handleBusinessException(
                        BusinessException ex, WebRequest request) {
                ApiErrorResponse apiErrorResponse = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                ex.getMessage()
                // ,request.getDescription(false).replace("uri=", "")
                );
                return new ResponseEntity<>(apiErrorResponse, HttpStatus.BAD_REQUEST);
        }

        // 修改：處理 InvalidRequestException
        @ExceptionHandler(InvalidRequestException.class)
        public ResponseEntity<ApiErrorResponse> handleInvalidRequestException(
                        InvalidRequestException ex, WebRequest request) {

                ApiErrorResponse apiErrorResponse = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                ex.getMessage()
                // ,request.getDescription(false).replace("uri=", "")
                );

                return new ResponseEntity<>(apiErrorResponse, HttpStatus.BAD_REQUEST);
        }

        // 處理MethodArgumentNotValidException
        @Override
        // 移除 @ExceptionHandler(MethodArgumentNotValidException.class) 因為繼承的方法已有
        protected ResponseEntity<Object> handleMethodArgumentNotValid(
                        MethodArgumentNotValidException ex,
                        org.springframework.http.HttpHeaders headers, // 修正 import
                        HttpStatusCode status, // 使用 HttpStatusCode
                        WebRequest request) {

                // 用 StringBuilder 來組合多個錯誤訊息
                StringBuilder errorMessage = new StringBuilder("Validation failed: ");
                ex.getBindingResult().getFieldErrors().forEach(fieldError -> {
                    errorMessage.append("[Field: '")
                                .append(fieldError.getField())
                                .append("', Message: '")
                                .append(fieldError.getDefaultMessage())
                                .append("']; ");
                });
                // 移除最後多餘的分號和空格
                if (errorMessage.length() > "Validation failed: ".length()) {
                    errorMessage.setLength(errorMessage.length() - 2);
                }

                ApiErrorResponse apiErrorResponse = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Validation Error", // 可以給一個更明確的錯誤類型
                                errorMessage.toString() // 使用組合後的訊息
                );
                return new ResponseEntity<>(apiErrorResponse, HttpStatus.BAD_REQUEST);
        }

        // 修改：處理所有其他未捕捉的 RuntimeException
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleGenericException(
                        Exception ex, WebRequest request) {
                logger.error("未預期的錯誤發生: ", ex);

                ApiErrorResponse apiErrorResponse = new ApiErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "Internal Server Error",
                                "發生未預期的錯誤。"
                // ,request.getDescription(false).replace("uri=", "")
                );

                return new ResponseEntity<>(apiErrorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
}
