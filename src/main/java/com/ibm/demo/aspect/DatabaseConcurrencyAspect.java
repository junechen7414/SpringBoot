package com.ibm.demo.aspect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ibm.demo.annotation.DatabaseConcurrencyLimit;
import com.ibm.demo.config.properties.DatabaseConcurrencyProperties;
import com.ibm.demo.exception.BusinessLogicCheck.ServiceOverloadedException;

import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
@Order(1) // 確保在 LoggingAspect 之前或之後執行，通常流量控制要在最外層
public class DatabaseConcurrencyAspect {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConcurrencyAspect.class);

    private final DatabaseConcurrencyProperties properties;

    // 存放不同資源的信號量
    private final Map<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

    @Around("@annotation(limitAnnotation)")
    public Object limit(ProceedingJoinPoint pjp, DatabaseConcurrencyLimit limitAnnotation) throws Throwable {
        String resourceName = limitAnnotation.value();

        // 兩層：application.properties中 Map 指定值 > 全域預設值
        final int limitCount = properties.getLimits()
                .getOrDefault(resourceName, properties.getDefaultLimit());

        // 動態初始化 Semaphore
        Semaphore semaphore = semaphoreMap.computeIfAbsent(resourceName, k -> new Semaphore(limitCount));

        // 嘗試取得許可，最多等待 3 秒
        boolean acquired = semaphore.tryAcquire(3, TimeUnit.SECONDS);

        if (!acquired) {
            log.warn("Concurrency limit reached for [{}]. Current available permits: {}. Rejecting request.",
                    resourceName, semaphore.availablePermits());
            throw new ServiceOverloadedException("系統忙碌中，請稍後再試。");
        }

        try {
            return pjp.proceed();
        } finally {
            // 務必在 finally 釋放，確保不會造成資源洩漏
            semaphore.release();
        }
    }
}