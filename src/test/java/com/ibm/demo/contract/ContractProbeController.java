package com.ibm.demo.contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.exception.SystemException;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 專供錯誤契約測試用的探針 controller：每個端點只負責拋出一種例外，讓
 * {@link com.ibm.demo.GlobalExceptionHandler} 的每條路徑都能經過**真實的 DispatcherServlet
 * 與 Jackson 序列化**被斷言。
 *
 * <p>為什麼不拿真正的 domain controller 來測：它們要拉整條 service/repository 鏈，
 * 而錯誤契約與業務邏輯無關 —— 用探針把「wire format」這件事單獨隔離出來，
 * 契約測試才不會因為某個 service 改了實作而連帶變紅。
 *
 * <p>本類別**不在** component scan 範圍生效（test 目錄），僅由契約測試明確指名載入。
 */
@RestController
@RequestMapping("/contract-probe")
public class ContractProbeController {

    /** 成功側探針：用來確認「成功回應不受錯誤契約影響」。 */
    @GetMapping("/ok")
    public ProbeResponse ok() {
        return new ProbeResponse("ok");
    }

    /** 依 {@link ErrorCode} 名稱拋出 BusinessException，涵蓋所有 4xx 業務錯誤。 */
    @GetMapping("/business/{errorCode}")
    public ProbeResponse business(@PathVariable("errorCode") String errorCode) {
        throw new BusinessException(ErrorCode.valueOf(errorCode), "探針訊息: " + errorCode);
    }

    @GetMapping("/bulkhead")
    public ProbeResponse bulkhead() {
        throw BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("contract-probe"));
    }

    @GetMapping("/circuit-breaker")
    public ProbeResponse circuitBreaker() {
        throw CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("contract-probe"));
    }

    @GetMapping("/rate-limiter")
    public ProbeResponse rateLimiter() {
        throw RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("contract-probe"));
    }

    @GetMapping("/optimistic-lock")
    public ProbeResponse optimisticLock() {
        throw new ObjectOptimisticLockingFailureException("ProbeEntity", 1);
    }

    @GetMapping("/system-error")
    public ProbeResponse systemError() {
        throw new SystemException("下游 API 呼叫失敗")
                .with("remoteStatus", 502)
                .with("remoteMessage", "Bad Gateway");
    }

    /**
     * 直接拋出 {@link org.springframework.security.access.AccessDeniedException}：模擬未來導入方法級
     * authZ（{@code @PreAuthorize}）時的情形。它在 DispatcherServlet 內被拋出，因此會先命中
     * {@code @RestControllerAdvice}，而非 Security 的 ExceptionTranslationFilter。
     */
    @GetMapping("/access-denied")
    public ProbeResponse accessDenied() {
        throw new org.springframework.security.access.AccessDeniedException("探針拒絕存取");
    }

    @GetMapping("/unexpected")
    public ProbeResponse unexpected() {
        throw new IllegalStateException("探針刻意拋出的未預期例外");
    }

    /** {@code @Valid @RequestBody} → MethodArgumentNotValidException（含 field error 與 global error）。 */
    @PostMapping("/validate")
    public ProbeResponse validate(@Valid @RequestBody ProbeRequest request) {
        return new ProbeResponse("validated");
    }

    /** 缺少必填 query param → MissingServletRequestParameterException（父類別處理）。 */
    @GetMapping("/required-param")
    public ProbeResponse requiredParam(@RequestParam("amount") Integer amount) {
        return new ProbeResponse(String.valueOf(amount));
    }

    public record ProbeResponse(String value) {
    }

    /**
     * 同時帶得動 field error 與 global（class-level）error 的請求體 —— 後者是刻意的：
     * 現行 handler 只讀 {@code getFieldErrors()}，global error 會被靜默丟棄，
     * 這個型別讓那個缺口變成可斷言的事實。
     */
    @ProbeConsistent
    public record ProbeRequest(
            @NotNull(message = "must not be null") Integer accountId,
            @Positive(message = "must be positive") Integer quantity,
            Integer min,
            Integer max) {
    }

    /** class-level 約束：只為了產生一個 global error。 */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = ProbeConsistentValidator.class)
    public @interface ProbeConsistent {
        String message() default "min must not exceed max";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class ProbeConsistentValidator implements ConstraintValidator<ProbeConsistent, ProbeRequest> {
        @Override
        public boolean isValid(ProbeRequest value, ConstraintValidatorContext context) {
            if (value == null || value.min() == null || value.max() == null) {
                return true;
            }
            return value.min() <= value.max();
        }
    }
}
