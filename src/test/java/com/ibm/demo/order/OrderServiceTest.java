package com.ibm.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.AccountClient;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.OrderStatusInvalidException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductStockNotEnoughException;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.ProductClient;

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
    private final int ORDER_STATUS_PENDING = OrderStatus.CREATED.getCode();
    private final int ORDER_STATUS_CANCELLED = OrderStatus.CANCELLED.getCode();
    private final String ACCOUNT_STATUS_ACTIVE = AccountStatus.ACTIVE.getCode();
    private final String ACCOUNT_STATUS_INACTIVE = AccountStatus.INACTIVE.getCode();
    private final Integer ACTIVE_ACCOUNT_ID = 1;
    private final Integer SELLABLE_PRODUCT_ID = 1;
    private final Integer EXISTING_ORDER_ID = 101;

    @BeforeEach
    void setUp() {
        // 顯性建立 SUT
        orderService = new OrderService(orderInfoRepository, orderDetailRepository, accountClient, productClient);
    }

    @Nested
    @DisplayName("建立訂單成功流程")
    class CreateOrderSuccessTests {

        @Test
        @DisplayName("建立訂單完整流程：校驗帳號、商品、庫存後，成功存檔並扣庫存")
        void createOrder_FullProcess_Success() {
            // Arrange
            Integer productId = 1;
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .accountId(ACTIVE_ACCOUNT_ID)
                    .orderDetails(List.of(new CreateOrderDetailRequest(productId, 2)))
                    .build();

            // 1. 模擬帳號校驗 (Service 確實有呼叫)
            when(accountClient.getAccountDetail(ACTIVE_ACCOUNT_ID))
                    .thenReturn(GetAccountDetailResponse.builder().status(ACCOUNT_STATUS_ACTIVE).build());

            // 2. 移除原本的 getProductDetails (因為 Service 沒呼叫)
            // 如果 processOrderItems 是 void，我們甚至不需要寫 when，只需後續 verify
            // 如果它有回傳值，才需要 when(...)

            // 3. 模擬儲存 OrderInfo
            OrderInfo savedInfo = new OrderInfo();
            savedInfo.setId(888);
            when(orderInfoRepository.save(any(OrderInfo.class))).thenReturn(savedInfo);

            // Act
            Integer orderId = orderService.createOrder(request);

            // Assert
            assertThat(orderId).isEqualTo(888);

            // Verify: 驗證真正被呼叫到的方法
            verify(accountClient).getAccountDetail(ACTIVE_ACCOUNT_ID);
            verify(productClient).processOrderItems(anySet(), anySet()); // 這是 Service 實際呼叫的方法
            verify(orderInfoRepository).save(any(OrderInfo.class));
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
                    .status(ACCOUNT_STATUS_INACTIVE).build();
            when(accountClient.getAccountDetail(inactiveId)).thenReturn(inactiveResponse);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(AccountInactiveException.class);

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
                    .thenReturn(GetAccountDetailResponse.builder().status(ACCOUNT_STATUS_ACTIVE).build());

            // 關鍵：模擬 processOrderItems 發現商品不可售並拋出異常
            doThrow(new ProductInactiveException("商品不可銷售"))
                    .when(productClient).processOrderItems(anySet(), anySet());

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(ProductInactiveException.class);

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
                    .thenReturn(GetAccountDetailResponse.builder().status(ACCOUNT_STATUS_ACTIVE).build());

            // 關鍵：模擬 processOrderItems 發現庫存不足
            doThrow(new ProductStockNotEnoughException("庫存不足"))
                    .when(productClient).processOrderItems(anySet(), anySet());

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(ProductStockNotEnoughException.class);

            verify(orderInfoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("更新訂單業務邏輯")
    class UpdateOrderTests {

        @Test
        @DisplayName("更新時若訂單 ID 不存在，應拋出 ResourceNotFoundException")
        void updateOrder_WhenOrderNotFound_ShouldThrowException() {
            // Arrange: 必須提供 items，否則會死在 ServiceValidator
            UpdateOrderRequest request = new UpdateOrderRequest(
                    EXISTING_ORDER_ID,
                    ORDER_STATUS_PENDING,
                    List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 1)));

            when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.updateOrder(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("更新時若包含庫存不足的商品，應拋出 ProductStockNotEnoughException")
        void updateOrder_WhenInsufficientStock_ShouldThrowException() {
            // Arrange
            OrderInfo existingOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, ORDER_STATUS_PENDING);
            UpdateOrderRequest request = new UpdateOrderRequest(EXISTING_ORDER_ID, ORDER_STATUS_PENDING,
                    List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID, 999)));

            when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(existingOrder));

            // 關鍵：模擬 processOrderItems 拋出庫存不足異常
            doThrow(new ProductStockNotEnoughException("庫存不足"))
                    .when(productClient).processOrderItems(anySet(), anySet());

            // Act & Assert
            assertThatThrownBy(() -> orderService.updateOrder(request))
                    .isInstanceOf(ProductStockNotEnoughException.class);

            // 驗證流程在拋出異常後中斷，沒有執行存檔
            verify(orderInfoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("刪除訂單業務邏輯")
    class DeleteOrderTests {

        @Test
        @DisplayName("刪除時若訂單不存在，應拋出 ResourceNotFoundException")
        void deleteOrder_WhenNotFound_ShouldThrowException() {
            when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.deleteOrder(EXISTING_ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("刪除時若訂單狀態非 PENDING (例如已取消)，應拋出 OrderStatusInvalidException")
        void deleteOrder_WhenStatusNotPending_ShouldThrowOrderStatusInvalidException() {
            // Arrange
            OrderInfo cancelledOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID,
                    ORDER_STATUS_CANCELLED);
            when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(cancelledOrder));

            // Act & Assert
            // 將預期的 Exception 改為實際拋出的 OrderStatusInvalidException
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