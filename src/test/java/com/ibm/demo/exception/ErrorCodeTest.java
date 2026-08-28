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
}
