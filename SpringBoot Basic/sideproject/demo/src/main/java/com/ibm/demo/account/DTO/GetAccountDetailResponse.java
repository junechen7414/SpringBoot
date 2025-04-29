package com.ibm.demo.account.DTO;

// import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAccountDetailResponse {
    private String name;
    private String status;
    // LocalDate createDate;
    // LocalDate modifiedDate;
}
