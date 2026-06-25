package com.ibm.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.OrderStatusInvalidException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductStockNotEnoughException;
import com.ibm.demo.exception.BusinessLogicCheck.ResourceNotFoundException;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.DTO.internal.AdjustStockRequest;
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
                @DisplayName("建立訂單時，若帳號不具下單資格，應拋出 AccountInactiveException")
                void createOrder_WhenAccountIsInactive_ShouldThrowException() {
                        // Arrange
                        Integer inactiveId = 2;
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(inactiveId)
                                        .items(List.of(CreateOrderDetailRequest.builder()
                                                        .productId(SELLABLE_PRODUCT_ID)
                                                        .quantity(1)
                                                        .build()))
                                        .build();

                        // 關鍵：帳戶領域判定不具資格並拋出例外
                        doThrow(new AccountInactiveException("帳戶狀態:停用"))
                                        .when(accountClient).assertCanPlaceOrder(inactiveId);

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(AccountInactiveException.class)
                                        .hasMessageContaining("帳戶狀態");

                        verifyNoInteractions(productClient);
                        verifyNoInteractions(orderTransactionalService);
                }

                @Test
                @DisplayName("建立訂單時，若商品不可銷售，應由 ProductClient 拋出異常")
                void createOrder_WhenProductNotSellable_ShouldThrowException() {
                        // Arrange
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .items(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 1)))
                                        .build();

                        // 關鍵：模擬 reserveStock 發現商品不可售並拋出異常
                        doThrow(new ProductInactiveException("商品不可銷售"))
                                        .when(productClient)
                                        .reserveStock(any());

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(ProductInactiveException.class)
                                        .hasMessageContaining("商品不可銷售");

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
                        doThrow(new ProductStockNotEnoughException("庫存不足"))
                                        .when(productClient)
                                        .reserveStock(any());
                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(ProductStockNotEnoughException.class)
                                        .hasMessageContaining("庫存不足");

                        // 驗證：既然拋異常了，後面的交易服務絕對不該執行
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
                        OrderInfo existingOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID,
                                        STATUS_CREATED);
                        UpdateOrderRequest request = new UpdateOrderRequest(
                                        EXISTING_ORDER_ID,
                                        STATUS_CREATED,
                                        List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 5)));

                        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(existingOrder));

                        // Act
                        orderService.updateOrder(request);

                        // Assert
                        // 驗證呼叫了交易層服務來儲存
                        verify(orderTransactionalService).updateOrder(request, existingOrder);
                        verify(productClient).adjustStock(any(AdjustStockRequest.class)); // 庫存處理依然由商品服務負責
                }

                @Test
                @DisplayName("更新訂單時交易服務失敗，應觸發補償反轉庫存並拋出原始異常")
                void updateOrder_WhenTransactionFails_ShouldCompensateAndThrow() {
                        // Arrange
                        OrderInfo existingOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID,
                                        STATUS_CREATED);
                        UpdateOrderRequest request = new UpdateOrderRequest(
                                        EXISTING_ORDER_ID,
                                        STATUS_CREATED,
                                        List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 5)));

                        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(existingOrder));

                        // 模擬交易服務拋出異常
                        doThrow(new RuntimeException("DB update failed"))
                                        .when(orderTransactionalService).updateOrder(any(), any());

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.updateOrder(request))
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
                        // Arrange
                        // 關鍵修正：增加 String scenario 參數來接收 @CsvSource 的第一欄文字
                        UpdateOrderRequest request = new UpdateOrderRequest(
                                        nonExistentId,
                                        STATUS_CREATED,
                                        List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 1)));

                        when(orderInfoRepository.findById(nonExistentId)).thenReturn(Optional.empty());

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.updateOrder(request))
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Order not found")
                                        .hasMessageContaining(String.valueOf(nonExistentId));

                        // 實務建議：驗證後續的 Client 或交易服務都不應該被執行
                        verifyNoInteractions(productClient);
                        verifyNoInteractions(orderTransactionalService);
                }

                @Test
                @DisplayName("更新時若包含庫存不足的商品，應拋出 ProductStockNotEnoughException")
                void updateOrder_WhenInsufficientStock_ShouldThrowException() {
                        // Arrange
                        OrderInfo existingOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID,
                                        STATUS_CREATED);
                        UpdateOrderRequest request = new UpdateOrderRequest(EXISTING_ORDER_ID, STATUS_CREATED,
                                        List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 999)));

                        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(existingOrder));

                        // 關鍵：模擬 adjustStock 拋出庫存不足異常
                        doThrow(new ProductStockNotEnoughException("庫存不足"))
                                        .when(productClient)
                                        .adjustStock(any(AdjustStockRequest.class));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.updateOrder(request))
                                        .isInstanceOf(ProductStockNotEnoughException.class)
                                        .hasMessageContaining("庫存不足");

                        // 驗證流程在拋出異常後中斷，沒有執行交易服務
                        verifyNoInteractions(orderTransactionalService);
                }
        }

        @Nested
        @DisplayName("刪除訂單成功流程")
        class DeleteOrderSuccessTests {

                @Test
                @DisplayName("刪除存在的 CREATED 訂單應成功並釋放庫存")
                void deleteOrder_Success() {
                        // Arrange
                        OrderInfo order = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, STATUS_CREATED);
                        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(order));

                        // Act
                        orderService.deleteOrder(EXISTING_ORDER_ID);

                        // Assert
                        verify(orderTransactionalService).deleteOrder(order, order.getVersion());
                        verify(productClient).releaseStock(any());
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
                        // Arrange
                        // 關鍵修正：增加 String scenario 參數來接收 @CsvSource 的第一欄文字
                        when(orderInfoRepository.findById(nonExistentId)).thenReturn(Optional.empty());

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.deleteOrder(nonExistentId))
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Order not found")
                                        .hasMessageContaining(String.valueOf(nonExistentId));

                        // Verify: 實務防禦性驗證
                        // 確保沒有呼叫刪除動作，且沒有與庫存服務進行任何交互
                        verifyNoInteractions(productClient);
                        verifyNoInteractions(orderTransactionalService);
                }

                @Test
                @DisplayName("刪除時若訂單狀態非 CREATED (例如已取消)，應拋出 OrderStatusInvalidException")
                void deleteOrder_WhenStatusNotPending_ShouldThrowOrderStatusInvalidException() {
                        // Arrange
                        OrderInfo cancelledOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID,
                                        STATUS_CANCELLED);
                        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(cancelledOrder));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.deleteOrder(EXISTING_ORDER_ID))
                                        .isInstanceOf(OrderStatusInvalidException.class)
                                        .hasMessageContaining("訂單狀態不允許刪除");

                        // 驗證：狀態不對，不應該執行後續任何儲存或扣庫存動作
                        verifyNoInteractions(productClient);
                        verifyNoInteractions(orderTransactionalService);
                }

                @Test
                @DisplayName("刪除時若發生樂觀鎖衝突，應觸發補償重新預留庫存並拋出原始異常")
                void deleteOrder_WhenOptimisticLockingConflict_ShouldCompensateAndThrow() {
                        // Arrange
                        OrderInfo order = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, STATUS_CREATED);
                        order.setVersion(1);
                        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(order));

                        // 模擬交易服務拋出樂觀鎖異常
                        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(OrderInfo.class, EXISTING_ORDER_ID))
                                        .when(orderTransactionalService).deleteOrder(order, 1);

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.deleteOrder(EXISTING_ORDER_ID))
                                        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

                        verify(orderTransactionalService).deleteOrder(order, 1);
                        // Verify: 先釋放庫存，補償時再重新預留庫存
                        verify(productClient).releaseStock(any());
                        verify(productClient).reserveStock(any());
                }
        }

        // --- Helper Methods ---
        private OrderInfo createTestOrderInfo(Integer orderId, Integer accountId, Integer status) {
                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setId(orderId);
                orderInfo.setAccountId(accountId);
                orderInfo.setStatus(status);
                orderInfo.setOrderDetails(new ArrayList<>());
                return orderInfo;
        }
}
