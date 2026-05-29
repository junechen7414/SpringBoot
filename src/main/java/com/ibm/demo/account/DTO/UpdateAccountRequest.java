package com.ibm.demo.account.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateAccountRequest(

        @NotBlank(message = "Name is required") @Size(max = 50, message = "50 characters max") String name,

        @NotBlank(message = "Status is required")
        @Pattern(regexp = "^[YN]$", message = "Status must be either 'Y' (Active) or 'N' (Inactive)")
        String status) {
}
