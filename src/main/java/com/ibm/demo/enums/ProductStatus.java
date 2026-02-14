package com.ibm.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductStatus {
    AVAILABLE(1001, "可銷售"),
    INAVAILABLE(1002, "不可銷售");

    private final int code;
    private final String description;

}