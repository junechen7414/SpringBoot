package com.ibm.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AccountStatus {
    ACTIVE("Y", "啟用"),
    INACTIVE("N", "停用");

    private final String code;
    private final String description;

}
