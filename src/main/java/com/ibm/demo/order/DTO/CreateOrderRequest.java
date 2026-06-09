package com.ibm.demo.order.DTO;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
@Schema(description = "建立訂單請求")
public record CreateOrderRequest(
        @NotNull(message = "Account ID is required")
        @Digits(integer = 10, fraction = 0, message = "10 characters max")
        @Positive(message = "Account ID must be positive")
        @Schema(description = "帳戶 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer accountId,

        @Valid
        @NotEmpty(message = "Order items are required")
        @Schema(description = "訂單明細項目列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CreateOrderDetailRequest> items) {
}
