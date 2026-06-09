package com.ibm.demo.account.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "更新帳戶請求")
public record UpdateAccountRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "50 characters max")
        @Schema(description = "帳戶名稱", example = "Bobby", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotBlank(message = "Status is required")
        @Pattern(regexp = "^[YN]$", message = "Status must be either 'Y' (Active) or 'N' (Inactive)")
        @Schema(description = "啟用狀態 (Y=啟用, N=停用)", example = "Y", allowableValues = {"Y", "N"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String status) {
}
