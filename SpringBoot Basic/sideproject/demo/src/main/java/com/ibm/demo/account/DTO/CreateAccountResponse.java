package com.ibm.demo.account.DTO;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountResponse {
    private int id;
    private String name;
    private String status;
    private LocalDate createDate;
}
