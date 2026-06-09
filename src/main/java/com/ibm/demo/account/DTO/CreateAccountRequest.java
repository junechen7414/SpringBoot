package com.ibm.demo.account.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "建立帳戶請求")
public record CreateAccountRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "50 characters max")
        @Schema(description = "帳戶名稱", example = "Bobby", requiredMode = Schema.RequiredMode.REQUIRED)
        String name) {
}
