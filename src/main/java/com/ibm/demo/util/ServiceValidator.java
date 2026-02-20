package com.ibm.demo.util;

import com.ibm.demo.exception.InvalidRequestException;

public class ServiceValidator {
    // 私有建構子，防止這個工具類別被執行實例化
    private ServiceValidator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void validateNotNull(Object object, String parameterName) {
        if (object == null) {
            throw new InvalidRequestException(parameterName + " cannot be null.");
        }
    }

    public static void validateNotEmpty(Object object, String parameterName) {
        if (object instanceof String str && str.isEmpty()) {
            throw new InvalidRequestException(parameterName + " cannot be empty.");
        }
        if (object instanceof Iterable<?> iterable && !iterable.iterator().hasNext()) {
            throw new InvalidRequestException(parameterName + " cannot be empty.");
        }
    }
}
