package com.ibm.demo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface DatabaseConcurrencyLimit {
    /**
     * * 限流資源標識符。
     * 1. 相同名稱的資源將共用同一Semaphore。
     * 2. 預設為 "default"，若多個方法皆未指定名稱，則都會是這個default的名稱，而如第1點說的會共用同一Semaphore。
     * 3. 目前依業務模組命名（如 "OrderService"），以實現不同業務間的限流隔離。
     */
    String value() default "default";
    // 移除 limit()，將數值控制權完全交給 Properties 檔，
    // 可以統一管理測試參數，避免這裡有魔術數字
}