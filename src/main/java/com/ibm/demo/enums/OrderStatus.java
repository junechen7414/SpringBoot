package com.ibm.demo.enums;

public enum OrderStatus {
    CREATED(1001, "訂單建立"),
    CANCELLED(1003, "訂單取消");

    private final int code;

    OrderStatus(int code, String description) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}