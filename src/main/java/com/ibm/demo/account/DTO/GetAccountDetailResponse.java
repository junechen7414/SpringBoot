package com.ibm.demo.account.DTO;

import lombok.Builder;

@Builder
public record GetAccountDetailResponse(
                String name,
                String status) {
}