package com.ibm.demo.account.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "帳戶詳細資訊回應")
public record GetAccountDetailResponse(
        @Schema(description = "帳戶名稱", example = "Bobby")
        String name,

        @Schema(description = "啟用狀態 (Y=啟用, N=停用)", example = "Y", allowableValues = {"Y", "N"})
        String status) {
}
