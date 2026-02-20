package com.ibm.demo.util;

import lombok.Builder;

@Builder
public record OrderItemRequest(
        Integer productId,
        Integer quantity) {

}
