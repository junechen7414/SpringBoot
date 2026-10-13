package com.ibm.demo.account.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * 更新帳戶請求。停用（Deactivate）只走 {@code DELETE /account/{id}}，
 * 見 {@code docs/adr/0001-soft-delete-only-for-termination.md}。
 */
@Builder
@Schema(description = "更新帳戶請求")
public record UpdateAccountRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "50 characters max")
        @Schema(description = "帳戶名稱", example = "Bobby", requiredMode = Schema.RequiredMode.REQUIRED)
        String name) {
}
