package com.ibm.demo.aspect;

import java.util.Arrays;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    // 修改 Pointcut，使其更專注於 Controller 和 Service 層，避免攔截過多無關方法
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Service *)")
    public void serviceAndControllerLayer() {}

    // 使用 @Around 環繞通知來記錄方法執行時間和返回值
    // 這個 Advice 整合了原本的 @Before 和 @Around，避免重複攔截
    @Around("serviceAndControllerLayer()")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        logger.info("==> Entering method: {}.{}() with arguments: {}", className, methodName,
                Arrays.toString(joinPoint.getArgs()));

        long startTime = System.currentTimeMillis();

        Object result = joinPoint.proceed(); // 執行目標方法

        long endTime = System.currentTimeMillis();
        logger.info("<== Exiting method: {}.{}(); Execution time: {} ms; Result: {}",
                className, methodName, (endTime - startTime), result);

        return result;
    }

    // 在目標方法拋出例外後記錄日誌
    // 這會在 GlobalExceptionHandler 處理之前記錄，追蹤原始錯誤位置
    @AfterThrowing(pointcut = "serviceAndControllerLayer()", throwing = "ex")
    public void logException(JoinPoint joinPoint, Throwable ex) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        logger.error("!!! Exception in method: {}.{}() with cause = '{}'",
                className, methodName, ex.getCause() != null ? ex.getCause() : "NULL", ex);
        // 注意：這裡的 ex 是原始例外，GlobalExceptionHandler 之後還是會處理它並回傳適當的 HTTP Response
        // 這個日誌主要是為了開發和除錯時能更詳細地了解例外發生的上下文
    }
}
