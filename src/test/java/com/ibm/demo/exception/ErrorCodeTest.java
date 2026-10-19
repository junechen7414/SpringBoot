package com.ibm.demo.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link ErrorCode} 的不變條件。
 *
 * <p>這個 enum 是整個 API 錯誤契約的單一註冊表：對外的 {@code code}、{@code type}、HTTP status
 * 全部由它決定。以下幾條規則是「code 與 type 不可能分岔」這個設計的全部依據，因此值得逐一釘住 ——
 * 新增常數時若違反了任何一條，這裡會馬上紅，而不是等下游對著奇怪的 type 除錯。
 */
@DisplayName("ErrorCode 不變條件")
class ErrorCodeTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("code 即常數名 —— 程式裡的識別碼與線路上的識別碼不可能分岔")
    void codeEqualsConstantName(ErrorCode errorCode) {
        assertThat(errorCode.getCode()).isEqualTo(errorCode.name());
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("type 由 code 機械推導，格式為 urn:problem:kebab-case")
    void typeIsDerivedFromCode(ErrorCode errorCode) {
        assertThat(errorCode.getType()).isEqualTo(ErrorCode.typeOf(errorCode.getCode()));
        assertThat(errorCode.getType().toString())
                .isEqualTo("urn:problem:" + errorCode.name().toLowerCase().replace('_', '-'));
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("每個 code 都必須是錯誤狀態（4xx/5xx）—— 2xx 的「錯誤碼」是矛盾")
    void statusIsAnError(ErrorCode errorCode) {
        assertThat(errorCode.getStatus().isError()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("title 是這類問題的固定摘要，不得為空")
    void titleIsPresent(ErrorCode errorCode) {
        assertThat(errorCode.getTitle()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("固定說明只屬於基礎設施類錯誤（非 4xx 業務錯誤）；有就不得為空白")
    void fixedDetailBelongsToInfrastructureCodes(ErrorCode errorCode) {
        if (errorCode.getFixedDetail() != null) {
            assertThat(errorCode.getFixedDetail()).isNotBlank();
        }
        // 業務錯誤的 detail 由 throw 點決定，這裡若有固定說明就會蓋掉它
        assertThat(errorCode.getFixedDetail() == null)
                .as("%s 是業務錯誤，不該有固定說明", errorCode)
                .isEqualTo(isBusinessCode(errorCode));
    }

    /** 登錄在「業務錯誤」區段的那六個，與 {@code ErrorCode} 的分區註解一致。 */
    private static boolean isBusinessCode(ErrorCode errorCode) {
        return switch (errorCode) {
            case ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED, PRODUCT_ALREADY_EXIST,
                    PRODUCT_STILL_HAS_RESERVATION_CAN_NOT_BE_DELETED, PRODUCT_STOCK_NOT_ENOUGH,
                    RESOURCE_NOT_FOUND, INVALID_REQUEST -> true;
            // VALIDATION_FAILED 的 detail 隨欄位錯誤變化，也沒有固定說明
            case VALIDATION_FAILED -> true;
            default -> false;
        };
    }
}
