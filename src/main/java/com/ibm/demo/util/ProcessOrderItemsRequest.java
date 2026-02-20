package com.ibm.demo.util;

import java.util.Set;

import lombok.Builder;

@Builder
public record ProcessOrderItemsRequest(
    Set<OrderItemRequest> originalItems,
    Set<OrderItemRequest> updatedItems
) {
    
}
