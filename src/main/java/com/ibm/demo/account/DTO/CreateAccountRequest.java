package com.ibm.demo.account.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
                @NotBlank(message = "Name is required") @Size(max = 50, message = "50 characters max") String name) {
}