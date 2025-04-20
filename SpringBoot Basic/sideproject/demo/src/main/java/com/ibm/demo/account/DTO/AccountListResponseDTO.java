package com.ibm.demo.account.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountListResponseDTO {
    private int id;
    private String name;
    private String status;
}
