package com.ibm.demo.enums;

public enum AccountStatus {
    ACTIVE("Y", "啟用"),
    INACTIVE("N", "停用");

    private final String code;

    AccountStatus(String code, String description) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
