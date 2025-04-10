package com.example.demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect  // 宣告此類別為切面
@Component  // 讓 IoC 容器管理此切面元件
public class LoggingAspect {

    // 切面：在 com.example.demo 及其子套件中的所有方法執行前進行日誌紀錄
    @Before("execution(* com.example.demo..*.*(..))") // Modified pointcut to include subpackages
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("AOP Log - Entering method: " + joinPoint.getSignature().toShortString()); // Improved log message
    }
}
