package com.ibm.demo.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.ibm.demo.exception.SystemException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 訂單狀態。
 *
 * <p>{@code code} 上的 {@link JsonValue} 的作用與取捨見 {@link ProductStatus}。
 *
 * <p><b>1002 是刻意的空號</b>：由 1001 跳到 1003，中間那個值在本專案的歷史裡沒有留下任何線索
 * （沒有程式碼、migration 或文件引用它）。既然無法確定它曾代表什麼，就不要為了讓序號連續而
 * 拿去表示新狀態 —— 萬一 DB 或下游還存著舊的 1002，重用會讓兩種語意撞在同一個值上。
 */
@AllArgsConstructor
@Getter
public enum OrderStatus {
    CREATED(1001, "訂單建立"),
    CANCELLED(1003, "訂單取消");

    @JsonValue
    private final int code;
    private final String description;

    /**
     * 由 DB 欄位值反查列舉，供 entity → {@code OrderView} 映射使用。理由同
     * {@link ProductStatus#fromCode(int)}。
     */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new SystemException("DB 存有無法識別的訂單狀態").with("status", code);
    }
}
