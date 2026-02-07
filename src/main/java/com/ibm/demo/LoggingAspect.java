package com.ibm.demo;

import java.util.Arrays;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    // 定義一個 Pointcut，攔截 com.ibm.demo.service 包及其子包下所有類別的 public 方法
    @Pointcut("execution(public * com.ibm.demo.*..*.*(..))")
    public void globalLayerMethods() {
    }

    // 在目標方法執行前記錄日誌
    @Before("globalLayerMethods()")
    public void logMethodEntry(JoinPoint joinPoint) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        logger.info("==> Entering method: {}.{}() with arguments: {}", className, methodName,
                Arrays.toString(joinPoint.getArgs()));
    }

    // 使用 @Around 環繞通知來記錄方法執行時間和返回值
    @Around("globalLayerMethods()")
    public Object logExecutionTimeAndResult(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        Object result = joinPoint.proceed(); // 執行目標方法

        long endTime = System.currentTimeMillis();
        logger.info("<== Exiting method: {}.{}(); Execution time: {} ms; Result: {}",
                className, methodName, (endTime - startTime), result);
        return result;
    }

    // 在目標方法拋出例外後記錄日誌
    // 這會在 GlobalExceptionHandler 處理之前記錄，追蹤原始錯誤位置
    @AfterThrowing(pointcut = "globalLayerMethods()", throwing = "ex")
    public void logException(JoinPoint joinPoint, Throwable ex) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        logger.error("!!! Exception in method: {}.{}() exception = '{}'",
                className, methodName, ex.getMessage(), ex);
        // 注意：這裡的 ex 是原始例外，GlobalExceptionHandler 之後還是會處理它並回傳適當的 HTTP Response
        // 這個日誌主要是為了開發和除錯時能更詳細地了解例外發生的上下文
    }
}
