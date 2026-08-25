package com.ibm.demo.util;

import java.util.Collection;

import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;

public class ServiceValidator {
    // 私有建構子，防止這個工具類別被執行實例化
    private ServiceValidator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // null 檢查對「任何」參考型別都成立，因此收 Object 是正確的契約寬度。
    public static void validateNotNull(Object object, String parameterName) {
        if (object == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, parameterName + " cannot be null.");
        }
    }

    // 「是否為空」只對 String / Collection 有意義，因此以多載明確限定適用型別。
    // 呼叫端一律寫 validateNotEmpty(...)，由編譯器依參數型別挑對應版本；
    // 傳入不適用的型別（例如 Integer）會在「編譯期」被擋下，而非靜默略過。
    // 未來要支援新的可空型別（例如 Map、陣列），在此新增一個多載即可，呼叫端不受影響。
    public static void validateNotEmpty(String value, String parameterName) {
        if (value == null || value.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, parameterName + " cannot be empty.");
        }
    }

    public static void validateNotEmpty(Collection<?> value, String parameterName) {
        if (value == null || value.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, parameterName + " cannot be empty.");
        }
    }
}
