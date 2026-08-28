package com.ibm.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.AccountClient;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.OrderDeletionPlan;
import com.ibm.demo.order.DTO.OrderView;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.product.DTO.internal.AdjustStockRequest;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;
import com.ibm.demo.product.DTO.internal.StockChangeRequest;
import com.ibm.demo.product.ProductClient;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

        @Mock
        private OrderInfoRepository orderInfoRepository;
        @Mock
        private AccountClient accountClient;
        @Mock
        private ProductClient productClient;
        @Mock
        private OrderTransactionalService orderTransactionalService;

        private OrderService orderService;

        // 測試常數
        private final Integer STATUS_CREATED = OrderStatus.CREATED.getCode();
        private final Integer STATUS_CANCELLED = OrderStatus.CANCELLED.getCode();
        private final Integer ACTIVE_ACCOUNT_ID = 1;
        private final Integer SELLABLE_PRODUCT_ID = 1;
        private final Integer EXISTING_ORDER_ID = 101;

        @BeforeEach
        void setUp() {
                // 顯性建立 SUT (System Under Test)
                orderService = new OrderService(orderInfoRepository, accountClient,
                                productClient, orderTransactionalService);
        }

        @Nested
        @DisplayName("建立訂單成功流程")
        class CreateOrderSuccessTests {

                @Test
                @DisplayName("建立訂單完整流程：校驗帳號資格、預留庫存後，成功存檔")
                void createOrder_FullProcess_Success() {
                        // Arrange
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .items(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 2)))
                                        .build();

                        // 帳號資格校驗為 void，預設不拋例外即代表通過，無須 stub。

                        // 模擬交易服務層的行為
                        when(orderTransactionalService.createOrder(any(CreateOrderRequest.class))).thenReturn(888);

                        // Act
                        Integer orderId = orderService.createOrder(request);

                        // Assert
                        assertThat(orderId).isEqualTo(888);

                        // Verify: 驗證核心依賴的互動
                        verify(accountClient).assertCanPlaceOrder(ACTIVE_ACCOUNT_ID);
                        verify(productClient).reserveStock(any());

                        // Verify: 驗證對交易服務的呼叫，並用 ArgumentCaptor 捕獲傳遞的內容
                        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor
                                        .forClass(CreateOrderRequest.class);
                        verify(orderTransactionalService).createOrder(requestCaptor.capture());
                        assertThat(requestCaptor.getValue())
                                        .hasFieldOrPropertyWithValue("accountId", ACTIVE_ACCOUNT_ID);
                }

                @Test
                @DisplayName("建立訂單時交易服務失敗，應觸發補償釋放庫存並拋出原始異常")
                void createOrder_WhenTransactionFails_ShouldCompensateAndThrow() {
                        // Arrange
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .items(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 2)))
                                        .build();

                        // 模擬交易服務拋出異常
                        doThrow(new RuntimeException("DB connection failed"))
                                        .when(orderTransactionalService).createOrder(any(CreateOrderRequest.class));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(RuntimeException.class)
                                        .hasMessageContaining("DB connection failed");

                        // Verify: 先預留庫存，交易失敗後補償釋放庫存
                        verify(productClient).reserveStock(any());
                        verify(productClient).releaseStock(any());
                }
        }

        @Nested
        @DisplayName("建立訂單例外業務邏輯")
        class CreateOrderTests {

                @Test
                @DisplayName("建立訂單時，若帳號不具下單資格(停用或不存在，受 SQLRestriction 濾除而查無)，應傳播 ResourceNotFoundException")
                void createOrder_WhenAccountIneligible_ShouldThrowException() {
                        // Arrange
                        Integer ineligibleId = 2;
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ineligibleId)
                                        .items(List.of(CreateOrderDetailRequest.builder()
                                                        .productId(SELLABLE_PRODUCT_ID)
                                                        .quantity(1)
                                                        .build()))
                                        .build();

                        // 關鍵：帳戶領域判定不具資格，停用/不存在均查無 -> ResourceNotFoundException
                        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found with id: " + ineligibleId))
                                        .when(accountClient).assertCanPlaceOrder(ineligibleId);

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND)
                                        .hasMessageContaining("Account not found");

                        verifyNoInteractions(productClient);
                        verifyNoInteractions(orderTransactionalService);
                }

                @Test
                @DisplayName("建立訂單時，若商品不可銷售(受 SQLRestriction 濾除而查無)，應由 ProductClient 拋出 ResourceNotFoundException")
                void createOrder_WhenProductNotSellable_ShouldThrowException() {
                        // Arrange
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .items(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 1)))
                                        .build();

                        // 關鍵：不可售商品受 @SQLRestriction 濾除，reserveStock 視為查無而拋出 ResourceNotFoundException
                        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Products not found with IDs: " + SELLABLE_PRODUCT_ID))
                                        .when(productClient)
                                        .reserveStock(any());

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND)
                                        .hasMessageContaining("Products not found");

                        // 驗證：既然拋異常了，後面的交易服務絕對不該執行
                        verifyNoInteractions(orderTransactionalService);
                }

                @Test
                @DisplayName("建立訂單時，若庫存不足，應由 ProductClient 拋出異常")
                void createOrder_WhenStockNotEnough_ShouldThrowException() {
                        // Arrange
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .items(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 999)))
                                        .build();

                        // 關鍵：模擬 reserveStock 發現庫存不足
                        doThrow(new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH, "庫存不足"))
                                        .when(productClient)
                                        .reserveStock(any());
                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_STOCK_NOT_ENOUGH)
                                        .hasMessageContaining("庫存不足");

                        // 驗證：既然拋異常了，後面的交易服務絕對不該執行
                        verifyNoInteractions(orderTransactionalService);
                }

                @Test
                @DisplayName("建立訂單時，若同一商品出現多筆明細(數量不同)，應拋出 InvalidRequestException")
                void createOrder_WhenDuplicateProduct_ShouldThrowException() {
                        // Arrange：同一 productId 兩筆、數量不同 —— 以 productId 判重應攔下
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .items(List.of(
                                                        new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 2),
                                                        new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 5)))
                                        .build();

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST)
                                        .hasMessageContaining("同一訂單中同一商品只能有一筆明細");

                        // 帳號資格校驗在去重之前，故仍被呼叫；去重失敗後不得預留庫存或進交易服務
                        verify(accountClient).assertCanPlaceOrder(ACTIVE_ACCOUNT_ID);
                        verifyNoInteractions(productClient);
                        verifyNoInteractions(orderTransactionalService);
                }
        }

        @Nested
        @DisplayName("更新訂單成功流程")
        class UpdateOrderSuccessTests {

                @Test
                @DisplayName("更新訂單狀態與商品明細，應成功儲存並同步庫存")
                void updateOrder_Success() {
                        // Arrange
                        UpdateOrderRequest request = new UpdateOrderRequest(
                                        STATUS_CREATED,
                                        List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 5)));

                        when(orderTransactionalService.loadOrderView(EXISTING_ORDER_ID)).thenReturn(
                                        new OrderView(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, STATUS_CREATED, List.of()));

                        // Act
                        orderService.updateOrder(EXISTING_ORDER_ID, request);

                        // Assert：交易層以 orderId + request 更新（交易內自行載入 managed 實體），庫存由商品服務同步
                        verify(orderTransactionalService).updateOrder(EXISTING_ORDER_ID, request);
                        verify(productClient).adjustStock(any(AdjustStockRequest.class));
                }

                @Test
                @DisplayName("更新訂單時交易服務失敗，應觸發補償反轉庫存並拋出原始異常")
                void updateOrder_WhenTransactionFails_ShouldCompensateAndThrow() {
                        // Arrange
                        UpdateOrderRequest request = new UpdateOrderRequest(
                                        STATUS_CREATED,
                                        List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 5)));

                        when(orderTransactionalService.loadOrderView(EXISTING_ORDER_ID)).thenReturn(
                                        new OrderView(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, STATUS_CREATED, List.of()));

                        // 模擬交易服務拋出異常
                        doThrow(new RuntimeException("DB update failed"))
                                        .when(orderTransactionalService).updateOrder(any(), any());

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.updateOrder(EXISTING_ORDER_ID, request))
                                        .isInstanceOf(RuntimeException.class)
                                        .hasMessageContaining("DB update failed");

                        // Verify: adjustStock 被呼叫兩次（一次調整庫存，一次補償調整回原狀）
                        verify(productClient, org.mockito.Mockito.times(2))
                                        .adjustStock(any(AdjustStockRequest.class));
                }
        }

        @Nested
        @DisplayName("更新訂單業務邏輯")
        class UpdateOrderTests {

                @ParameterizedTest(name = "[{index}] {0} (ID: {1})")
                @CsvSource({
                                "測試一般不存在ID, 888",
                                "測試負數非法ID, -1"
                })
                @DisplayName("更新時若訂單 ID 不存在，應拋出 ResourceNotFoundException")
                void updateOrder_WhenOrderNotFound_ShouldThrowException(String scenario, Integer nonExistentId) {
                        // Arrange：NotFound 由 loadOrderView（交易內載入）拋出
                        UpdateOrderRequest request = new UpdateOrderRequest(
                                        STATUS_CREATED,
                                        List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 1)));

                        when(orderTransactionalService.loadOrderView(nonExistentId))
                                        .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found with ID: " + nonExistentId));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.updateOrder(nonExistentId, request))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND)
                                        .hasMessageContaining("Order not found")
                                        .hasMessageContaining(String.valueOf(nonExistentId));

                        // 載入失敗 → 不動庫存、不執行更新
                        verifyNoInteractions(productClient);
                        verify(orderTransactionalService, never()).updateOrder(any(), any());
                }

                @Test
                @DisplayName("更新時若包含庫存不足的商品，應拋出 ProductStockNotEnoughException")
                void updateOrder_WhenInsufficientStock_ShouldThrowException() {
                        // Arrange
                        UpdateOrderRequest request = new UpdateOrderRequest(STATUS_CREATED,
                                        List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 999)));

                        when(orderTransactionalService.loadOrderView(EXISTING_ORDER_ID)).thenReturn(
                                        new OrderView(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, STATUS_CREATED, List.of()));

                        // 關鍵：模擬 adjustStock 拋出庫存不足異常
                        doThrow(new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH, "庫存不足"))
                                        .when(productClient)
                                        .adjustStock(any(AdjustStockRequest.class));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.updateOrder(EXISTING_ORDER_ID, request))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_STOCK_NOT_ENOUGH)
                                        .hasMessageContaining("庫存不足");

                        // 驗證流程在拋出異常後中斷，沒有執行交易服務的更新
                        verify(orderTransactionalService, never()).updateOrder(any(), any());
                }

                @Test
                @DisplayName("更新訂單時，若同一商品出現多筆明細(數量不同)，應拋出 InvalidRequestException")
                void updateOrder_WhenDuplicateProduct_ShouldThrowException() {
                        // Arrange：同一 productId 兩筆、數量不同 —— 以 productId 判重應在查 DB 前攔下
                        UpdateOrderRequest request = new UpdateOrderRequest(
                                        STATUS_CREATED,
                                        List.of(
                                                        new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 2),
                                                        new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 5)));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.updateOrder(EXISTING_ORDER_ID, request))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST)
                                        .hasMessageContaining("同一訂單中同一商品只能有一筆明細");

                        // 去重失敗在查 DB 前就擋下：不得載入、調整庫存或進交易服務
                        verifyNoInteractions(productClient);
                        verifyNoInteractions(orderTransactionalService);
                }
        }

        @Nested
        @DisplayName("刪除訂單成功流程")
        class DeleteOrderSuccessTests {

                @Test
                @DisplayName("刪除存在的 CREATED 訂單應成功並釋放庫存")
                void deleteOrder_Success() {
                        // Arrange：載入/驗證/明細萃取已收斂至 prepareOrderDeletion，模擬其回傳純資料計畫
                        Integer version = 5;
                        OrderDeletionPlan plan = new OrderDeletionPlan(ACTIVE_ACCOUNT_ID, version,
                                        Set.of(new OrderItemRequest(SELLABLE_PRODUCT_ID, 2)));
                        when(orderTransactionalService.prepareOrderDeletion(EXISTING_ORDER_ID)).thenReturn(plan);

                        // Act
                        orderService.deleteOrder(EXISTING_ORDER_ID);

                        // Assert
                        verify(productClient).releaseStock(new StockChangeRequest(plan.items()));
                        verify(orderTransactionalService).deleteOrder(EXISTING_ORDER_ID, version);
                }
        }

        @Nested
        @DisplayName("刪除訂單業務邏輯")
        class DeleteOrderTests {

                @ParameterizedTest(name = "[{index}] {0} (ID: {1})")
                @CsvSource({
                                "測試一般不存在ID, 888",
                                "測試負數非法ID, -1"
                })
                @DisplayName("刪除時若訂單不存在，應拋出 ResourceNotFoundException")
                void deleteOrder_WhenNotFound_ShouldThrowException(String scenario, Integer nonExistentId) {
                        // Arrange：載入/驗證已收斂至 prepareOrderDeletion，故在此模擬其拋出 NotFound
                        when(orderTransactionalService.prepareOrderDeletion(nonExistentId))
                                        .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found with ID: " + nonExistentId));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.deleteOrder(nonExistentId))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND)
                                        .hasMessageContaining("Order not found")
                                        .hasMessageContaining(String.valueOf(nonExistentId));

                        // Verify: 準備階段失敗 → 不應釋放庫存、也不應執行刪除
                        verifyNoInteractions(productClient);
                        verify(orderTransactionalService, never()).deleteOrder(any(), any());
                }

                @Test
                @DisplayName("刪除時若訂單狀態非 CREATED (例如已取消)，應拋出 OrderStatusInvalidException")
                void deleteOrder_WhenStatusNotPending_ShouldThrowOrderStatusInvalidException() {
                        // Arrange：狀態驗證已收斂至 prepareOrderDeletion
                        when(orderTransactionalService.prepareOrderDeletion(EXISTING_ORDER_ID))
                                        .thenThrow(new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "訂單狀態不允許刪除，目前狀態: " + STATUS_CANCELLED));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.deleteOrder(EXISTING_ORDER_ID))
                                        .isInstanceOf(BusinessException.class)
                                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_STATUS_INVALID)
                                        .hasMessageContaining("訂單狀態不允許刪除");

                        // 驗證：狀態不對，不應該執行後續任何扣庫存或刪除動作
                        verifyNoInteractions(productClient);
                        verify(orderTransactionalService, never()).deleteOrder(any(), any());
                }

                @Test
                @DisplayName("刪除時若發生樂觀鎖衝突，應觸發補償重新預留庫存並拋出原始異常")
                void deleteOrder_WhenOptimisticLockingConflict_ShouldCompensateAndThrow() {
                        // Arrange
                        Integer version = 1;
                        OrderDeletionPlan plan = new OrderDeletionPlan(ACTIVE_ACCOUNT_ID, version,
                                        Set.of(new OrderItemRequest(SELLABLE_PRODUCT_ID, 2)));
                        when(orderTransactionalService.prepareOrderDeletion(EXISTING_ORDER_ID)).thenReturn(plan);

                        // 模擬交易服務拋出樂觀鎖異常
                        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(OrderInfo.class, EXISTING_ORDER_ID))
                                        .when(orderTransactionalService).deleteOrder(EXISTING_ORDER_ID, version);

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.deleteOrder(EXISTING_ORDER_ID))
                                        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

                        verify(orderTransactionalService).deleteOrder(EXISTING_ORDER_ID, version);
                        // Verify: 先釋放庫存，補償時再重新預留庫存
                        verify(productClient).releaseStock(new StockChangeRequest(plan.items()));
                        verify(productClient).reserveStock(new StockChangeRequest(plan.items()));
                }
        }

}
