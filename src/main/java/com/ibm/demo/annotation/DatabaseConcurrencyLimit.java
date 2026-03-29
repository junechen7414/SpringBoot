package com.ibm.demo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DatabaseConcurrencyLimit {
    /** 資源名稱，例如 "OrderService" */
    String value() default "default";
    /** 允許的最大併發數 */
    int limit() default 10;
}