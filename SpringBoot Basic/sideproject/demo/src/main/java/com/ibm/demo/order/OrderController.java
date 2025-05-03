package com.ibm.demo.order;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.CreateOrderResponse;
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.DTO.UpdateOrderResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders") // 基礎路徑
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Create Order
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest) {
        CreateOrderResponse createOrderResponse = orderService.createOrder(createOrderRequest);
        return ResponseEntity.ok(createOrderResponse);
    }

    // Read Order List
    @GetMapping("/getList/{accountId}")
    public ResponseEntity<List<GetOrderListResponse>> getOrderList(@PathVariable Integer accountId) {
        List<GetOrderListResponse> getOrderListResponse = orderService.getOrderList(accountId);
        return ResponseEntity.ok(getOrderListResponse);
    }

    // Read Order Detail
    @GetMapping("/getDetail/{orderId}")
    public ResponseEntity<GetOrderDetailResponse> getOrderDetails(@PathVariable Integer orderId) {
        GetOrderDetailResponse getOrderDetailResponse = orderService.getOrderDetails(orderId);
        return ResponseEntity.ok(getOrderDetailResponse);
    }

    // Update Order
    @PutMapping
    public ResponseEntity<UpdateOrderResponse> updateOrder(@Valid @RequestBody UpdateOrderRequest updateOrderRequest) {
        UpdateOrderResponse updateOrderResponse = orderService.updateOrder(updateOrderRequest);
        return ResponseEntity.ok(updateOrderResponse);
    }

    // Delete Order
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Integer orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    // 帳戶ID是否存在任何訂單中
    @GetMapping("/AccountIdIsInOrder/{accountId}")
    public ResponseEntity<Boolean> AccountIdIsInOrder(@PathVariable Integer accountId) {
        boolean isExist = orderService.AccountIdIsInOrder(accountId);
        return ResponseEntity.ok(isExist);
    }
}
