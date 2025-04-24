package com.ibm.demo.account.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 50, message = "100 characters max")
    private String name;

    @Size(max = 1, message = "1 characters max, either 'Y' or 'N'")
    private String status;
}
