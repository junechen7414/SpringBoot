package com.ibm.demo.account.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "帳戶列表回應")
public record GetAccountListResponse(
        @Schema(description = "帳戶 ID", example = "1")
        Integer id,

        @Schema(description = "帳戶名稱", example = "Bobby")
        String name,

        @Schema(description = "啟用狀態 (Y=啟用, N=停用)", example = "Y", allowableValues = {"Y", "N"})
        String status) {
}
