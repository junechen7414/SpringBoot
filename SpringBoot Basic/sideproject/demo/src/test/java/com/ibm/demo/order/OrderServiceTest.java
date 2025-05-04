package com.ibm.demo.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.AccountClient;
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.OrderStatusInvalidException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductStockNotEnoughException;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.CreateOrderResponse;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.DTO.UpdateOrderResponse;
import com.ibm.demo.order.Entity.OrderDetail;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.DTO.GetProductDetailResponse;

@ExtendWith(MockitoExtension.class) // 使用 Mockito Extension
class OrderServiceTest {

    // --- 常數 ---
    private static final Integer ORDER_STATUS_PENDING = 1001; // 處理中
    private static final Integer ORDER_STATUS_COMPLETED = 1002; // 假設有已完成狀態
    private static final Integer ORDER_STATUS_DELETED = 1003; // 已刪除
    private static final Integer TEST_ACCOUNT_ID = 1;
    private static final Integer TEST_ORDER_ID = 101;
    private static final Integer TEST_PRODUCT_ID_1 = 1;
    private static final Integer TEST_PRODUCT_ID_2 = 2;
    // -----------

    @Mock
    private OrderInfoRepository orderInfoRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private ProductClient productClient;

    @InjectMocks // 自動注入 Mock 物件到 OrderService
    private OrderService orderService;

    // Logger for test output if needed
    // private static final Logger logger =
    // LoggerFactory.getLogger(OrderServiceTest.class);

    @BeforeEach
    void setUp() {
        // MockitoAnnotations.openMocks(this); // @ExtendWith(MockitoExtension.class)
        // 取代了這個
    }

    // --- Helper Methods ---

    private OrderInfo createTestOrderInfo(Integer orderId, Integer accountId, Integer status) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setAccountId(accountId);
        orderInfo.setStatus(status);
        orderInfo.setCreateDate(LocalDate.now().minusDays(1)); // Simulate existing order
        orderInfo.setModifiedDate(LocalDate.now().minusDays(1));
        orderInfo.setOrderDetails(new ArrayList<>()); // Initialize detail list
        return orderInfo;
    }

    private OrderDetail createTestOrderDetail(OrderInfo orderInfo, Integer productId, Integer quantity) {
        OrderDetail detail = new OrderDetail(orderInfo, productId, quantity);
        // Assuming OrderDetail might have an ID after saving, but not needed for most
        // tests here
        orderInfo.getOrderDetails().add(detail); // Link back to order info
        return detail;
    }

    private GetProductDetailResponse createTestProductDetailResponse(Integer productId, String name, BigDecimal price,
            Integer stock) {
        GetProductDetailResponse dto = new GetProductDetailResponse(); // 假設 DTO 有無參數建構子
        dto.setName(name);
        dto.setPrice(price);
        dto.setStockQty(stock);
        dto.setSaleStatus(1001); // Assume sellable
        return dto;
    }

    // --- Test Cases ---

    @Test
    @DisplayName("建立訂單成功時，訂單狀態應初始化為處理中(1001)")
    void createOrder_Success_ShouldSetStatusToPending() {
        // Arrange
        // 1. Request DTO
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        List<CreateOrderDetailRequest> detailRequests = new ArrayList<>();
        detailRequests.add(new CreateOrderDetailRequest(TEST_PRODUCT_ID_1, 5));
        request.setOrderDetails(detailRequests);

        // 2. Mock Account Validation
        doNothing().when(accountClient).validateActiveAccount(TEST_ACCOUNT_ID);

        // 3. Mock Product Details
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(TEST_PRODUCT_ID_1,
                createTestProductDetailResponse(TEST_PRODUCT_ID_1, "Product 1", new BigDecimal("10.00"), 100));
        when(productClient.getProductDetails(Set.of(TEST_PRODUCT_ID_1))).thenReturn(productDetailsMap);

        // 4. Mock Product Stock Update
        doNothing().when(productClient).updateProductsStock(anyMap());

        // 5. Mock OrderInfo Save
        // Use ArgumentCaptor to capture the OrderInfo passed to save
        ArgumentCaptor<OrderInfo> orderInfoCaptor = ArgumentCaptor.forClass(OrderInfo.class);
        // Simulate save returning an OrderInfo with generated ID and date
        when(orderInfoRepository.save(orderInfoCaptor.capture())).thenAnswer(invocation -> {
            OrderInfo orderToSave = invocation.getArgument(0);
            orderToSave.setId(TEST_ORDER_ID); // Simulate DB generated ID
            orderToSave.setCreateDate(LocalDate.now()); // Simulate DB generated date
            return orderToSave;
        });

        // 6. Mock OrderDetail Save
        when(orderDetailRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0)); // Return
                                                                                                            // the list
                                                                                                            // passed

        // Act
        CreateOrderResponse response = orderService.createOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals(TEST_ORDER_ID, response.getOrderId());
        assertEquals(TEST_ACCOUNT_ID, response.getAccountId());
        assertEquals(ORDER_STATUS_PENDING, response.getStatus(), "訂單狀態應為處理中(1001)");

        // Verify the status of the OrderInfo object *before* it was saved
        OrderInfo capturedOrderInfo = orderInfoCaptor.getValue();
        assertNotNull(capturedOrderInfo);
        assertEquals(ORDER_STATUS_PENDING, capturedOrderInfo.getStatus(), "傳遞給 repository 的 OrderInfo 狀態應為處理中(1001)");
        assertEquals(TEST_ACCOUNT_ID, capturedOrderInfo.getAccountId());

        // Verify interactions (optional but good practice)
        verify(accountClient, times(1)).validateActiveAccount(TEST_ACCOUNT_ID);
        verify(productClient, times(1)).getProductDetails(Set.of(TEST_PRODUCT_ID_1));
        verify(productClient, times(1)).updateProductsStock(anyMap());
        verify(orderInfoRepository, times(1)).save(any(OrderInfo.class));
        verify(orderDetailRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("建立訂單時，若帳號非活躍，應拋出 AccountInactiveException")
    void createOrder_WhenAccountIsInactive_ShouldThrowAccountInactiveException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setOrderDetails(List.of(new CreateOrderDetailRequest(TEST_PRODUCT_ID_1, 1))); // 即使有商品也不應處理

        // 模擬 AccountClient 在驗證時拋出例外
        doThrow(new AccountInactiveException("Account " + TEST_ACCOUNT_ID + " is inactive.")).when(accountClient)
                .validateActiveAccount(TEST_ACCOUNT_ID);

        // Act & Assert
        AccountInactiveException exception = assertThrows(AccountInactiveException.class, () -> {
            orderService.createOrder(request);
        });

        assertEquals("Account " + TEST_ACCOUNT_ID + " is inactive.", exception.getMessage());
        // Verify 確保沒有儲存任何訂單或明細
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("建立訂單時，若有商品不可銷售，應拋出 ProductInactiveException")
    void createOrder_WhenProductNotSellable_ShouldThrowProductInactiveException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setOrderDetails(List.of(
                new CreateOrderDetailRequest(TEST_PRODUCT_ID_1, 1), // Sellable
                new CreateOrderDetailRequest(TEST_PRODUCT_ID_2, 1)  // Not Sellable
        ));

        doNothing().when(accountClient).validateActiveAccount(TEST_ACCOUNT_ID);

        // Mock Product Details: Product 2 is not sellable (status 1002)
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        GetProductDetailResponse sellableProduct = createTestProductDetailResponse(TEST_PRODUCT_ID_1, "P1", BigDecimal.TEN, 10);
        GetProductDetailResponse notSellableProduct = createTestProductDetailResponse(TEST_PRODUCT_ID_2, "P2", BigDecimal.ONE, 5);
        notSellableProduct.setSaleStatus(1002); // <-- 設定為不可銷售
        productDetailsMap.put(TEST_PRODUCT_ID_1, sellableProduct);
        productDetailsMap.put(TEST_PRODUCT_ID_2, notSellableProduct);

        // 模擬 ProductClient 在獲取商品詳情時，因商品不可銷售而拋出例外
        when(productClient.getProductDetails(Set.of(TEST_PRODUCT_ID_1, TEST_PRODUCT_ID_2)))
                .thenThrow(new ProductInactiveException("商品不可銷售: ID=" + TEST_PRODUCT_ID_2)); // 模擬拋出例外

        // Act & Assert
        ProductInactiveException exception = assertThrows(ProductInactiveException.class, () -> { // 預期 ProductInactiveException
            orderService.createOrder(request);
        });

        assertTrue(exception.getMessage().contains("商品不可銷售: ID=" + TEST_PRODUCT_ID_2));

        // Verify no persistence occurred
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(productClient, never()).updateProductsStock(anyMap()); // Stock update should not happen
    }

    @Test
    @DisplayName("建立訂單時，若有商品庫存不足，應拋出 ProductStockNotEnoughException")
    void createOrder_WhenInsufficientStock_ShouldThrowProductStockNotEnoughException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setOrderDetails(List.of(new CreateOrderDetailRequest(TEST_PRODUCT_ID_1, 15))); // Request 15

        doNothing().when(accountClient).validateActiveAccount(TEST_ACCOUNT_ID);

        // Mock Product Details: Product 1 is sellable but only has 10 in stock
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(TEST_PRODUCT_ID_1, createTestProductDetailResponse(TEST_PRODUCT_ID_1, "P1", BigDecimal.TEN, 10)); // Stock is 10
        when(productClient.getProductDetails(Set.of(TEST_PRODUCT_ID_1))).thenReturn(productDetailsMap);

        // Act & Assert
        ProductStockNotEnoughException exception = assertThrows(ProductStockNotEnoughException.class, () -> {
            orderService.createOrder(request);
        });

        // 驗證例外訊息符合 calculateNewStock 的格式
        String expectedMsgPart1 = String.format("商品 %d 庫存不足", TEST_PRODUCT_ID_1);
        String expectedMsgPart2 = String.format("目前庫存: %d", 10); // 測試設定的庫存
        String expectedMsgPart3 = String.format("訂單新數量: %d", 15); // 測試請求的數量
        assertTrue(exception.getMessage().contains(expectedMsgPart1), "Exception message should contain product ID part");
        assertTrue(exception.getMessage().contains(expectedMsgPart2), "Exception message should contain current stock part");
        assertTrue(exception.getMessage().contains(expectedMsgPart3), "Exception message should contain requested quantity part");

        // Verify no persistence occurred
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(productClient, never()).updateProductsStock(anyMap()); // Stock update should not happen
    }

    @Test
    @DisplayName("更新訂單時，若訂單狀態非處理中(1001)，應拋出 OrderStatusInvalidException")
    void updateOrder_WhenStatusNotPending_ShouldThrowOrderStatusInvalidException() {
        // Arrange
        // 1. Create an existing order with a non-pending status (e.g., Completed)
        OrderInfo existingOrder = createTestOrderInfo(TEST_ORDER_ID, TEST_ACCOUNT_ID, ORDER_STATUS_COMPLETED); // Status
                                                                                                               // is NOT
                                                                                                               // 1001

        // 2. Mock findById to return this order
        when(orderInfoRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // 3. Prepare an update request (content doesn't matter much for this specific
        // test)
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(TEST_ORDER_ID);
        request.setItems(List.of(new UpdateOrderDetailRequest(TEST_PRODUCT_ID_1, 1))); // Example item

        // Act & Assert
        OrderStatusInvalidException exception = assertThrows(OrderStatusInvalidException.class, () -> {
            orderService.updateOrder(request);
        });

        assertTrue(exception.getMessage().contains("訂單狀態不允許更新商品項目"));
        assertTrue(exception.getMessage().contains(String.valueOf(ORDER_STATUS_COMPLETED)));

        // Verify findById was called, but no further processing (like getting product
        // details or saving) occurred
        verify(orderInfoRepository, times(1)).findById(TEST_ORDER_ID);
        verify(productClient, never()).getProductDetails(anySet());
        verify(productClient, never()).updateProductsStock(anyMap());
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(orderDetailRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("更新訂單時，若訂單狀態為處理中(1001)，應允許更新 (僅驗證狀態檢查通過)")
    void updateOrder_WhenStatusIsPending_ShouldProceed() {
        // Arrange
        // 1. Create an existing order with PENDING status
        OrderInfo existingOrder = createTestOrderInfo(TEST_ORDER_ID, TEST_ACCOUNT_ID, ORDER_STATUS_PENDING);
        // Add an existing detail
        createTestOrderDetail(existingOrder, TEST_PRODUCT_ID_1, 5);

        // 2. Mock findById
        when(orderInfoRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // 3. Prepare update request (e.g., change quantity)
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(TEST_ORDER_ID);
        List<UpdateOrderDetailRequest> updatedItems = new ArrayList<>();
        updatedItems.add(new UpdateOrderDetailRequest(TEST_PRODUCT_ID_1, 8)); // Update quantity
        request.setItems(updatedItems);

        // 4. Mock Product Details (needed for update logic)
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(TEST_PRODUCT_ID_1,
                createTestProductDetailResponse(TEST_PRODUCT_ID_1, "Product 1", new BigDecimal("10.00"), 100));
        when(productClient.getProductDetails(Set.of(TEST_PRODUCT_ID_1))).thenReturn(productDetailsMap);

        // 5. Mock stock update
        doNothing().when(productClient).updateProductsStock(anyMap());

        // 6. Mock OrderDetail save (for the updated detail)
        when(orderDetailRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // 7. Mock OrderInfo save
        when(orderInfoRepository.save(any(OrderInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        // We expect this *not* to throw OrderStatusInvalidException
        // We can assert the response or verify interactions to confirm processing
        // continued
        UpdateOrderResponse response = orderService.updateOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals(TEST_ORDER_ID, response.getOrderId());
        // Further assertions on response content could be added, but the main goal here
        // is to ensure no OrderStatusInvalidException was thrown due to the status
        // check.

        // Verify that processing continued beyond the status check
        verify(orderInfoRepository, times(1)).findById(TEST_ORDER_ID);
        verify(productClient, times(1)).getProductDetails(anySet()); // Called because status was valid
        verify(productClient, times(1)).updateProductsStock(anyMap()); // Called
        verify(orderDetailRepository, times(1)).saveAll(anyList()); // Called for update
        verify(orderInfoRepository, times(1)).save(any(OrderInfo.class)); // Called
    }

    @Test
    @DisplayName("更新訂單時，若有商品不可銷售，應拋出 ProductInactiveException")
    void updateOrder_WhenProductNotSellable_ShouldThrowProductInactiveException() {
        // Arrange
        OrderInfo existingOrder = createTestOrderInfo(TEST_ORDER_ID, TEST_ACCOUNT_ID, ORDER_STATUS_PENDING);
        createTestOrderDetail(existingOrder, TEST_PRODUCT_ID_1, 2); // Original detail

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(TEST_ORDER_ID);
        request.setItems(List.of(
                new UpdateOrderDetailRequest(TEST_PRODUCT_ID_1, 1), // Still sellable
                new UpdateOrderDetailRequest(TEST_PRODUCT_ID_2, 1)  // Add a non-sellable item
        ));

        when(orderInfoRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // Mock Product Details: Product 2 is not sellable
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        GetProductDetailResponse sellableProduct = createTestProductDetailResponse(TEST_PRODUCT_ID_1, "P1", BigDecimal.TEN, 10);
        GetProductDetailResponse notSellableProduct = createTestProductDetailResponse(TEST_PRODUCT_ID_2, "P2", BigDecimal.ONE, 5);
        notSellableProduct.setSaleStatus(1002); // <-- 設定為不可銷售
        productDetailsMap.put(TEST_PRODUCT_ID_1, sellableProduct);
        productDetailsMap.put(TEST_PRODUCT_ID_2, notSellableProduct);

        // 模擬 ProductClient 在獲取商品詳情時，因商品不可銷售而拋出例外
        when(productClient.getProductDetails(Set.of(TEST_PRODUCT_ID_1, TEST_PRODUCT_ID_2)))
                .thenThrow(new ProductInactiveException("商品不可銷售: ID=" + TEST_PRODUCT_ID_2)); // 模擬拋出例外

        // Act & Assert
        ProductInactiveException exception = assertThrows(ProductInactiveException.class, () -> { // 預期 ProductInactiveException
            orderService.updateOrder(request);
        });

        assertTrue(exception.getMessage().contains("商品不可銷售: ID=" + TEST_PRODUCT_ID_2));

        // Verify no persistence occurred beyond finding the initial order
        verify(orderInfoRepository, times(1)).findById(TEST_ORDER_ID);
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(orderDetailRepository, never()).deleteAll(anyList());
        verify(productClient, never()).updateProductsStock(anyMap());
    }

    @Test
    @DisplayName("更新訂單時，若有商品庫存不足，應拋出 ProductStockNotEnoughException")
    void updateOrder_WhenInsufficientStock_ShouldThrowProductStockNotEnoughException() { // 修正方法名中的例外類型
        // Arrange
        OrderInfo existingOrder = createTestOrderInfo(TEST_ORDER_ID, TEST_ACCOUNT_ID, ORDER_STATUS_PENDING);
        createTestOrderDetail(existingOrder, TEST_PRODUCT_ID_1, 2); // Original detail

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(TEST_ORDER_ID);
        request.setItems(List.of(new UpdateOrderDetailRequest(TEST_PRODUCT_ID_1, 15))); // Request 15

        when(orderInfoRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // Mock Product Details: Product 1 is sellable but only has 10 in stock
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(TEST_PRODUCT_ID_1, createTestProductDetailResponse(TEST_PRODUCT_ID_1, "P1", BigDecimal.TEN, 10)); // Stock is 10
        when(productClient.getProductDetails(Set.of(TEST_PRODUCT_ID_1))).thenReturn(productDetailsMap);

        // Act & Assert
        ProductStockNotEnoughException exception = assertThrows(ProductStockNotEnoughException.class, () -> {
            orderService.updateOrder(request);
        });

        // 驗證例外訊息符合 calculateNewStock 的格式
        String expectedMsgPart1 = String.format("商品 %d 庫存不足", TEST_PRODUCT_ID_1);
        String expectedMsgPart2 = String.format("目前庫存: %d", 10); // 測試設定的庫存
        String expectedMsgPart3 = String.format("訂單原數量: %d", 2); // 測試中訂單原數量
        String expectedMsgPart4 = String.format("訂單新數量: %d", 15); // 測試請求的數量
        assertTrue(exception.getMessage().contains(expectedMsgPart1), "Exception message should contain product ID part");
        assertTrue(exception.getMessage().contains(expectedMsgPart2), "Exception message should contain current stock part");
        assertTrue(exception.getMessage().contains(expectedMsgPart3), "Exception message should contain original quantity part");
        assertTrue(exception.getMessage().contains(expectedMsgPart4), "Exception message should contain requested quantity part");

        // Verify no persistence occurred beyond finding the initial order
        verify(orderInfoRepository, times(1)).findById(TEST_ORDER_ID);
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(orderDetailRepository, never()).deleteAll(anyList());
        verify(productClient, never()).updateProductsStock(anyMap());
    }

    @Test
    @DisplayName("刪除訂單時，若訂單狀態非處理中(1001)，應拋出 OrderStatusInvalidException")
    void deleteOrder_WhenStatusNotPending_ShouldThrowOrderStatusInvalidException() {
        // Arrange
        // 1. Create an existing order with a non-pending status
        OrderInfo existingOrder = createTestOrderInfo(TEST_ORDER_ID, TEST_ACCOUNT_ID, ORDER_STATUS_DELETED); // Status
                                                                                                             // is NOT
                                                                                                             // 1001

        // 2. Mock findById
        when(orderInfoRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // Act & Assert
        OrderStatusInvalidException exception = assertThrows(OrderStatusInvalidException.class, () -> {
            orderService.deleteOrder(TEST_ORDER_ID);
        });

        assertTrue(exception.getMessage().contains("訂單狀態不允許刪除"));
        assertTrue(exception.getMessage().contains(String.valueOf(ORDER_STATUS_DELETED)));

        // Verify findById was called, but no further processing occurred
        verify(orderInfoRepository, times(1)).findById(TEST_ORDER_ID);
        verify(productClient, never()).getProductDetails(anySet());
        verify(productClient, never()).updateProductsStock(anyMap());
        verify(orderInfoRepository, never()).save(any(OrderInfo.class)); // Save should not be called
    }

    @Test
    @DisplayName("刪除訂單成功時，訂單狀態應更新為已刪除(1003)")
    void deleteOrder_WhenStatusIsPending_ShouldUpdateStatusToDeleted() {
        // Arrange
        // 1. Create an existing order with PENDING status
        OrderInfo existingOrder = createTestOrderInfo(TEST_ORDER_ID, TEST_ACCOUNT_ID, ORDER_STATUS_PENDING);
        // Add some details to simulate restoring stock
        createTestOrderDetail(existingOrder, TEST_PRODUCT_ID_1, 3);
        createTestOrderDetail(existingOrder, TEST_PRODUCT_ID_2, 2);

        // 2. Mock findById
        when(orderInfoRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // 3. Mock Product Details (needed for stock restoration)
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(TEST_PRODUCT_ID_1,
                createTestProductDetailResponse(TEST_PRODUCT_ID_1, "P1", new BigDecimal("10"), 50));
        productDetailsMap.put(TEST_PRODUCT_ID_2,
                createTestProductDetailResponse(TEST_PRODUCT_ID_2, "P2", new BigDecimal("20"), 30));
        when(productClient.getProductDetails(Set.of(TEST_PRODUCT_ID_1, TEST_PRODUCT_ID_2)))
                .thenReturn(productDetailsMap);

        // 4. Mock stock update
        doNothing().when(productClient).updateProductsStock(anyMap());

        // 5. Mock OrderInfo save
        ArgumentCaptor<OrderInfo> orderInfoCaptor = ArgumentCaptor.forClass(OrderInfo.class);
        when(orderInfoRepository.save(orderInfoCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        orderService.deleteOrder(TEST_ORDER_ID);

        // Assert
        // Verify the status of the OrderInfo object passed to save
        OrderInfo capturedOrderInfo = orderInfoCaptor.getValue();
        assertNotNull(capturedOrderInfo);
        assertEquals(ORDER_STATUS_DELETED, capturedOrderInfo.getStatus(), "訂單狀態應更新為已刪除(1003)");
        assertEquals(TEST_ORDER_ID, capturedOrderInfo.getId()); // Ensure it's the correct order

        // Verify interactions
        verify(orderInfoRepository, times(1)).findById(TEST_ORDER_ID);
        verify(productClient, times(1)).getProductDetails(Set.of(TEST_PRODUCT_ID_1, TEST_PRODUCT_ID_2));
        verify(productClient, times(1)).updateProductsStock(anyMap());
        verify(orderInfoRepository, times(1)).save(any(OrderInfo.class)); // Verify save was called

    }
}
