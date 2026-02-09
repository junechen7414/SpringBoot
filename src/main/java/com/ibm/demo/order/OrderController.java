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
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.DTO.UpdateOrderRequest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/order") // 基礎路徑
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Create Order
    @Operation(summary = "建立新訂單", description = "帳戶狀態N拋出特定例外，之後若商品狀態不可銷售拋出特定例外，再來若商品庫存不足拋出特定例外，沒例外則更新商品庫存和新增訂單")
    @PostMapping("/create")
    public ResponseEntity<Integer> createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest) {
        Integer orderId = orderService.createOrder(createOrderRequest);
        return ResponseEntity.ok(orderId);
    }

    // Read Order List
    @GetMapping("/getList/{accountId}")
    public ResponseEntity<List<GetOrderListResponse>> getOrderList(@PathVariable Integer accountId) {
        List<GetOrderListResponse> getOrderListResponse = orderService.getOrderListByAccountId(accountId);
        return ResponseEntity.ok(getOrderListResponse);
    }

    // Read Order Detail
    @GetMapping("/getDetail/{orderId}")
    public ResponseEntity<GetOrderDetailResponse> getOrderDetails(@PathVariable Integer orderId) {
        GetOrderDetailResponse getOrderDetailResponse = orderService.getOrderDetailByOrderId(orderId);
        return ResponseEntity.ok(getOrderDetailResponse);
    }

    // Update Order
    @Operation(summary = "更新訂單內容", description = "不存在該訂單ID拋出NotFound，若商品狀態不可銷售拋出特定例外，再來若有商品庫存不足拋出特定例外，都沒更新商品庫存、訂單")
    @PutMapping("/update")
    public ResponseEntity<Void> updateOrder(@Valid @RequestBody UpdateOrderRequest updateOrderRequest) {
        orderService.updateOrder(updateOrderRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Order
    @Operation(summary = "刪除訂單", description = "訂單id不存在或狀態已經為1003取消拋出NotFound，都沒有則軟刪除更新OrderInfo的狀態資料欄位，真刪除OrderDetail並歸還商品庫存")
    @DeleteMapping("/delete/{orderId}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Integer orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    // 帳戶ID是否存在任何訂單中
    @Operation(summary = "檢查帳戶ID是否存在於任何訂單中", description = "判斷帳戶有沒有在訂單中，讓帳戶在更新狀態和刪除時檢核用，只在傳入的帳戶ID有關連訂單時回傳TRUE，傳入不存在和沒再訂單中的帳戶ID也回傳false")
    @GetMapping("/AccountIdIsInOrder/{accountId}")
    public ResponseEntity<Boolean> AccountIdIsInOrder(@PathVariable Integer accountId) {
        boolean isExist = orderService.ActiveAccountIdIsInOrder(accountId);
        return ResponseEntity.ok(isExist);
    }
}
