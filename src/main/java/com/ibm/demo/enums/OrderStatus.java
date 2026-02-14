package com.ibm.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OrderStatus {
    CREATED(1001, "訂單建立"),
    CANCELLED(1003, "訂單取消");

    private final int code;
    private final String description;

}