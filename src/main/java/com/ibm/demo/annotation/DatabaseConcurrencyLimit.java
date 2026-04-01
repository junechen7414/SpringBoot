package com.ibm.demo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DatabaseConcurrencyLimit {
    /** 資源名稱，例如 "OrderService" */
    String value() default "default";
    // 移除 limit()，將數值控制權完全交給 Properties 檔，
    // 可以統一管理測試參數，避免這裡有魔術數字
}