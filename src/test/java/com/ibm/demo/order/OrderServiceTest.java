package com.ibm.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.enums.AccountStatus;
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
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.util.ProcessOrderItemsRequest;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

        @Mock
        private OrderInfoRepository orderInfoRepository;
        @Mock
        private OrderDetailRepository orderDetailRepository;
        @Mock
        private AccountClient accountClient;
        @Mock
        private ProductClient productClient;

        private OrderService orderService;

        // 測試常數
        private final Integer STATUS_CREATED = OrderStatus.CREATED.getCode();
        private final Integer STATUS_CANCELLED = OrderStatus.CANCELLED.getCode();
        private final String STATUS_ACTIVE = AccountStatus.ACTIVE.getCode();
        private final String STATUS_INACTIVE = AccountStatus.INACTIVE.getCode();
        private final Integer ACTIVE_ACCOUNT_ID = 1;
        private final Integer SELLABLE_PRODUCT_ID = 1;
        private final Integer EXISTING_ORDER_ID = 101;

        @BeforeEach
        void setUp() {
                // 顯性建立 SUT
                orderService = new OrderService(orderInfoRepository, orderDetailRepository, accountClient,
                                productClient);
        }

        @Nested
        @DisplayName("建立訂單成功流程")
        class CreateOrderSuccessTests {

                @Test
                @DisplayName("建立訂單完整流程：校驗帳號、商品、庫存後，成功存檔並扣庫存")
                void createOrder_FullProcess_Success() {
                        // Arrange
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .orderDetails(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 2)))
                                        .build();

                        // 1. 模擬帳號校驗
                        when(accountClient.getAccountDetail(ACTIVE_ACCOUNT_ID))
                                        .thenReturn(GetAccountDetailResponse.builder().status(STATUS_ACTIVE).build());

                        // 2. 模擬儲存 OrderInfo
                        OrderInfo savedInfo = new OrderInfo();
                        savedInfo.setId(888);
                        when(orderInfoRepository.save(any(OrderInfo.class))).thenReturn(savedInfo);

                        // Act
                        Integer orderId = orderService.createOrder(request);

                        // Assert
                        assertThat(orderId).isEqualTo(888);

                        // Verify: 使用 ArgumentCaptor 驗證 OrderInfo 內容
                        ArgumentCaptor<OrderInfo> infoCaptor = ArgumentCaptor.forClass(OrderInfo.class);
                        verify(orderInfoRepository).save(infoCaptor.capture());
                        assertThat(infoCaptor.getValue())
                                        .hasFieldOrPropertyWithValue("accountId", ACTIVE_ACCOUNT_ID)
                                        .hasFieldOrPropertyWithValue("status", STATUS_CREATED);

                        verify(accountClient).getAccountDetail(ACTIVE_ACCOUNT_ID);
                        verify(productClient).processOrderItems(any(ProcessOrderItemsRequest.class));
                        verify(orderDetailRepository).saveAll(anyList());
                }
        }

        @Nested
        @DisplayName("建立訂單例外業務邏輯")
        class CreateOrderTests {

                @Test
                @DisplayName("建立訂單時，若帳號非活躍(N)，應拋出 AccountInactiveException")
                void createOrder_WhenAccountIsInactive_ShouldThrowException() {
                        // Arrange
                        Integer inactiveId = 2;
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(inactiveId)
                                        .orderDetails(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 1)))
                                        .build();

                        GetAccountDetailResponse inactiveResponse = GetAccountDetailResponse.builder()
                                        .status(STATUS_INACTIVE).build();
                        when(accountClient.getAccountDetail(inactiveId)).thenReturn(inactiveResponse);

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(AccountInactiveException.class)
                                        .hasMessageContaining("帳戶狀態");

                        verify(orderInfoRepository, never()).save(any());
                        verifyNoMoreInteractions(orderInfoRepository);
                }

                @Test
                @DisplayName("建立訂單時，若商品不可銷售，應由 ProductClient 拋出異常")
                void createOrder_WhenProductNotSellable_ShouldThrowException() {
                        // Arrange
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .orderDetails(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 1)))
                                        .build();

                        // 模擬帳戶正常
                        when(accountClient.getAccountDetail(ACTIVE_ACCOUNT_ID))
                                        .thenReturn(GetAccountDetailResponse.builder().status(STATUS_ACTIVE).build());

                        // 關鍵：模擬 processOrderItems 發現商品不可售並拋出異常
                        doThrow(new ProductInactiveException("商品不可銷售"))
                                        .when(productClient)
                                        .processOrderItems(any(ProcessOrderItemsRequest.class));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(ProductInactiveException.class)
                                        .hasMessageContaining("商品不可銷售");

                        // 驗證：既然拋異常了，後面的 save 絕對不該執行
                        verify(orderInfoRepository, never()).save(any());
                }

                @Test
                @DisplayName("建立訂單時，若庫存不足，應由 ProductClient 拋出異常")
                void createOrder_WhenStockNotEnough_ShouldThrowException() {
                        // Arrange
                        CreateOrderRequest request = CreateOrderRequest.builder()
                                        .accountId(ACTIVE_ACCOUNT_ID)
                                        .orderDetails(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID, 999)))
                                        .build();

                        when(accountClient.getAccountDetail(ACTIVE_ACCOUNT_ID))
                                        .thenReturn(GetAccountDetailResponse.builder().status(STATUS_ACTIVE).build());

                        // 關鍵：模擬 processOrderItems 發現庫存不足
                        doThrow(new ProductStockNotEnoughException("庫存不足"))
                                        .when(productClient)
                                        .processOrderItems(any(ProcessOrderItemsRequest.class));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.createOrder(request))
                                        .isInstanceOf(ProductStockNotEnoughException.class)
                                        .hasMessageContaining("庫存不足");

                        verify(orderInfoRepository, never()).save(any());
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
                        ArgumentCaptor<OrderInfo> captor = ArgumentCaptor.forClass(OrderInfo.class);
                        verify(orderInfoRepository).save(captor.capture());
                        assertThat(captor.getValue().getStatus()).isEqualTo(STATUS_CREATED);

                        verify(productClient).processOrderItems(any(ProcessOrderItemsRequest.class));
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

                        // 實務建議：驗證後續的 Repository 或 Client 動作都不應該被執行
                        verify(orderInfoRepository, never()).save(any());
                        verifyNoInteractions(productClient);
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

                        // 關鍵：模擬 processOrderItems 拋出庫存不足異常
                        doThrow(new ProductStockNotEnoughException("庫存不足"))
                                        .when(productClient)
                                        .processOrderItems(any(ProcessOrderItemsRequest.class));

                        // Act & Assert
                        assertThatThrownBy(() -> orderService.updateOrder(request))
                                        .isInstanceOf(ProductStockNotEnoughException.class)
                                        .hasMessageContaining("庫存不足");

                        // 驗證流程在拋出異常後中斷，沒有執行存檔
                        verify(orderInfoRepository, never()).save(any());
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
                        verify(orderInfoRepository).delete(order);
                        verify(productClient).processOrderItems(any(ProcessOrderItemsRequest.class));
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
                        verify(orderInfoRepository, never()).delete(any());
                        verifyNoInteractions(productClient);
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
                        verify(orderInfoRepository, never()).save(any());
                        verifyNoInteractions(productClient);
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
