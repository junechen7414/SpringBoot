package com.ibm.demo.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.ibm.demo.exception.SystemException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品銷售狀態。
 *
 * <p>{@code code} 上的 {@link JsonValue} 讓對外的 wire format 維持既有的數字（{@code 1001}）而非
 * Java 常數名，反序列化 Jackson 同樣依 code 比對。因此 DTO 只要把欄位宣告成本型別就同時得到
 * 「編譯期型別安全 + 未知值在進 controller 前就被擋成 400 + OpenAPI 自動列出值域」，
 * 而呼叫端契約完全不變。
 */
@Getter
@AllArgsConstructor
public enum ProductStatus {
    AVAILABLE(Codes.AVAILABLE, "可銷售"),
    UNAVAILABLE(Codes.UNAVAILABLE, "不可銷售");

    @JsonValue
    private final int code;
    private final String description;

    /**
     * 由 DB 欄位值反查列舉，供 entity → response DTO 映射使用。
     *
     * <p>查不到代表 DB 存了本應用無法表示的值（歷史髒資料或人工改動）。那是資料完整性問題、
     * 不是呼叫端的錯，所以走 {@link SystemException}（500 + ERROR log 帶 context），
     * 而不是把不明數值原封轉給呼叫端假裝沒事。
     */
    public static ProductStatus fromCode(int code) {
        for (ProductStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new SystemException("DB 存有無法識別的商品銷售狀態").with("saleStatus", code);
    }

    /** 與上方列舉常數一對一的編譯期常數，用途與限制見 {@link OrderStatus.Codes}。 */
    public static final class Codes {
        private Codes() {
            throw new UnsupportedOperationException("This is a constant holder and cannot be instantiated");
        }

        public static final int AVAILABLE = 1001;
        public static final int UNAVAILABLE = 1002;
    }
}
