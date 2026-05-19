package com.ibm.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import com.ibm.demo.exception.ApiErrorResponse;

public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("處理 BulkheadFullException，應回傳 503 Service Unavailable 狀態碼")
    void handleBulkheadFull_ShouldReturnServiceUnavailableStatus() {
        // Arrange
        BulkheadFullException ex = BulkheadFullException.createBulkheadFullException(
                io.github.resilience4j.bulkhead.Bulkhead.ofDefaults("test-bulkhead"));

        // Act
        ResponseEntity<ApiErrorResponse> responseEntity = globalExceptionHandler.handleBulkheadFull(ex);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(responseEntity.getBody().error()).isEqualTo("Service Overloaded");
        assertThat(responseEntity.getBody().message()).isEqualTo("系統負載過高，請稍後再試。");
    }

    @Test
    @DisplayName("處理樂觀鎖衝突例外，應回傳 409 Conflict 狀態碼")
    void handleOptimisticLockingFailure_ShouldReturnConflictStatus() {
        // Arrange
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException("Test Entity", 1L);

        // Act
        ResponseEntity<ApiErrorResponse> responseEntity = globalExceptionHandler.handleOptimisticLockingFailure(ex);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(responseEntity.getBody().error()).isEqualTo("Optimistic Locking Failure");
        assertThat(responseEntity.getBody().message()).isEqualTo("資料已被其他使用者修改，請重新整理後再試。");
    }


    @Test
    @DisplayName("處理 CallNotPermittedException，應回傳 503 Service Unavailable 狀態碼")
    void handleCallNotPermitted_ShouldReturnServiceUnavailableStatus() {
        // Arrange
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test-circuit-breaker"));

        // Act
        ResponseEntity<ApiErrorResponse> responseEntity = globalExceptionHandler.handleCallNotPermitted(ex);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(responseEntity.getBody().error()).isEqualTo("Circuit Breaker Open");
        assertThat(responseEntity.getBody().message()).isEqualTo("服務暫時不可用，請稍後再試。");
    }

}
