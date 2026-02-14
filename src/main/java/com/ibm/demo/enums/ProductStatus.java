package com.ibm.demo.enums;

public enum ProductStatus {
    AVAILABLE(1001, "可銷售"),
    INAVAILABLE(1002, "不可銷售");

    private final int code;

    ProductStatus(int code, String description) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}