package com.ibm.demo.order.DTO;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "Account ID is required")
    @Digits(integer = 10, fraction = 0, message = "10 characters max")
    @Positive(message = "Account ID must be positive")
    private int accountId;
    private int status;
    @Valid
    private List<CreateOrderDetailRequest> orderDetails;

}
