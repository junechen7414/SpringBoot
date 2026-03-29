package com.ibm.demo.util;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    ACCOUNT_INACTIVE(HttpStatus.BAD_REQUEST, "ACCOUNT_001", "帳戶尚未啟用"),
    ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED(HttpStatus.BAD_REQUEST, "ACCOUNT_002", "帳戶仍有訂單，無法刪除"),
    ORDER_STATUS_INVALID(HttpStatus.BAD_REQUEST, "ORDER_001", "訂單狀態無效"),
    PRODUCT_ALREADY_EXIST(HttpStatus.BAD_REQUEST, "PRODUCT_001", "商品名稱已存在"),
    PRODUCT_INACTIVE(HttpStatus.BAD_REQUEST, "PRODUCT_002", "商品尚未啟用"),
    PRODUCT_STOCK_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "PRODUCT_003", "商品庫存不足"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "SYS_001", "找不到資源"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "SYS_002", "無效的請求"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SYS_003", "服務暫時不可用");

    private final HttpStatus status;
    private final String code;
    private final String message;

}