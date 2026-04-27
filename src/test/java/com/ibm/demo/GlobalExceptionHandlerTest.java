package com.ibm.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.ibm.demo.exception.ApiErrorResponse;

public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

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
}
