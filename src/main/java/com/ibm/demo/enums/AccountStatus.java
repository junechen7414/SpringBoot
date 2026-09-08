package com.ibm.demo.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.ibm.demo.exception.SystemException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 帳戶啟用狀態。
 *
 * <p>{@code code} 上的 {@link JsonValue} 的作用與取捨見 {@link ProductStatus}；此處的 wire format
 * 是既有的單字元字串（{@code "Y"} / {@code "N"}），不是數字。
 */
@Getter
@AllArgsConstructor
public enum AccountStatus {
    ACTIVE("Y", "啟用"),
    INACTIVE("N", "停用");

    @JsonValue
    private final String code;
    private final String description;

    /**
     * 由 DB 欄位值反查列舉，供 entity → response DTO 映射使用。理由同
     * {@link ProductStatus#fromCode(int)}。
     */
    public static AccountStatus fromCode(String code) {
        for (AccountStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new SystemException("DB 存有無法識別的帳戶狀態").with("status", code);
    }
}
