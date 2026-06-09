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

import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.DTO.UpdateOrderRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Tag(name = "Order", description = "訂單管理 API")
public class OrderController {
    private final OrderService orderService;

    // Create Order
    @Operation(summary = "建立新訂單", description = "建立新訂單。先驗證帳戶是否啟用（不啟用則拋出 AccountInactiveException），檢查訂單內是否有重複商品（重複則拋出 InvalidRequestException），最後透過商品服務處理庫存（若狀態不可銷售或庫存不足由商品服務拋出異常）。成功則新增訂單主檔（預設狀態 1001）與明細。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "建立成功，回傳訂單 ID"),
            @ApiResponse(responseCode = "400", description = "參數驗證失敗、帳戶未啟用、重複商品或庫存不足",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "帳戶或商品不存在",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<Integer> createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest) {
        Integer orderId = orderService.createOrder(createOrderRequest);
        return ResponseEntity.ok(orderId);
    }

    // Read Order List
    @Operation(summary = "獲取帳戶訂單清單", description = "獲取該帳戶的所有有效訂單清單。受限於SQLRestriction規則，僅會回傳未被軟刪除且狀態為 1001 (CREATED) 的訂單。")
    @ApiResponse(responseCode = "200", description = "成功取得訂單列表")
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<GetOrderListResponse>> getOrderList(
            @Parameter(description = "帳戶 ID", example = "1", required = true)
            @PathVariable Integer accountId) {
        List<GetOrderListResponse> getOrderListResponse = orderService.getOrderListByAccountId(accountId);
        return ResponseEntity.ok(getOrderListResponse);
    }

    // Read Order Detail
    @Operation(summary = "獲取訂單詳細資訊", description = "獲取指定訂單的詳細資訊。受限於SQLRestriction規則，若訂單不存在、已被軟刪除或狀態非 1001 (CREATED)，將回傳 NotFound。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功取得訂單詳細資訊"),
            @ApiResponse(responseCode = "404", description = "訂單不存在",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<GetOrderDetailResponse> getOrderDetails(
            @Parameter(description = "訂單 ID", example = "1", required = true)
            @PathVariable Integer orderId) {
        GetOrderDetailResponse getOrderDetailResponse = orderService.getOrderDetailByOrderId(orderId);
        return ResponseEntity.ok(getOrderDetailResponse);
    }

    // Update Order
    @Operation(summary = "更新訂單內容", description = "更新訂單內容。若訂單不存在、已軟刪除或狀態非 1001 (CREATED)，將拋出 NotFound。接著檢查重複商品（重複則拋出 InvalidRequestException），並透過商品服務調整庫存（包含歸還舊品項庫存與扣除新品項庫存），最後更新訂單狀態與明細。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "參數驗證失敗、重複商品或庫存不足",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "訂單不存在",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<Void> updateOrder(@Valid @RequestBody UpdateOrderRequest updateOrderRequest) {
        orderService.updateOrder(updateOrderRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Order
    @Operation(summary = "刪除訂單", description = "刪除訂單。若訂單不存在、已軟刪除或狀態非 1001 (CREATED)，將拋出 NotFound。執行時會對訂單主檔與明細進行軟刪除，並透過商品服務歸還商品庫存。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "刪除成功"),
            @ApiResponse(responseCode = "404", description = "訂單不存在",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> deleteOrder(
            @Parameter(description = "訂單 ID", example = "1", required = true)
            @PathVariable Integer orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    // Check if account ID exists in any order
    @Operation(summary = "檢查帳戶ID是否存在於任何有效訂單中", description = "判斷帳戶是否有關聯的有效訂單，用於帳戶更新與刪除時的檢核。受限於系統規則，僅會針對未軟刪除且狀態為 1001 (CREATED) 的訂單進行判定。")
    @ApiResponse(responseCode = "200", description = "回傳布林值，true 表示有關聯訂單")
    @GetMapping("/account/{accountId}/exists")
    public ResponseEntity<Boolean> AccountIdIsInOrder(
            @Parameter(description = "帳戶 ID", example = "1", required = true)
            @PathVariable Integer accountId) {
        boolean isExist = orderService.isActiveAccountInOrder(accountId);
        return ResponseEntity.ok(isExist);
    }
}
