package com.ibm.demo.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.Collections;
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
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.DTO.OrderItemDTO;
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

    // --- Constants for Status Codes ---
    private static final int STATUS_PENDING = 1001;
    private static final int STATUS_COMPLETED = 1002; // Assuming 1002 is Completed
    private static final int STATUS_DELETED = 1003;
    private static final int PRODUCT_STATUS_SELLABLE = 1001;
    private static final int PRODUCT_STATUS_NOT_SELLABLE = 1002;

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
        dto.setSaleStatus(PRODUCT_STATUS_SELLABLE); // Assume sellable
        return dto;
    }

    // --- Test Cases ---

    @Test
    @DisplayName("建立訂單成功時，訂單狀態應初始化為處理中(1001)")
    void createOrder_Success_ShouldSetStatusToPendingAndReturnCorrectResponse() {
        // Arrange
        // 1. Request DTO
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(1); // 直接使用值
        List<CreateOrderDetailRequest> detailRequests = new ArrayList<>();
        detailRequests.add(new CreateOrderDetailRequest(1, 5)); // 直接使用值
        request.setOrderDetails(detailRequests);

        // 2. Mock Account Validation
        doNothing().when(accountClient).validateActiveAccount(1); // 直接使用值

        // 3. Mock Product Details
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(1,
                createTestProductDetailResponse(1, "Product 1", new BigDecimal("10.00"), 100)); // 直接使用值
        when(productClient.getProductDetails(Set.of(1))).thenReturn(productDetailsMap); // 直接使用值

        // 4. Mock Product Stock Update
        doNothing().when(productClient).updateProductsStock(anyMap());

        // 5. Mock OrderInfo Save
        // Use ArgumentCaptor to capture the OrderInfo passed to save
        ArgumentCaptor<OrderInfo> orderInfoCaptor = ArgumentCaptor.forClass(OrderInfo.class);
        // Simulate save returning an OrderInfo with generated ID and date
        when(orderInfoRepository.save(orderInfoCaptor.capture())).thenAnswer(invocation -> {
            OrderInfo orderToSave = invocation.getArgument(0);
            orderToSave.setId(101); // Simulate DB generated ID, 直接使用值
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
        assertEquals(101, response.getOrderId()); // 直接使用值
        assertEquals(1, response.getAccountId()); // 直接使用值
        assertEquals(STATUS_PENDING, response.getStatus(), "訂單狀態應為處理中(1001)");

        // Verify the status of the OrderInfo object *before* it was saved
        OrderInfo capturedOrderInfo = orderInfoCaptor.getValue();
        assertNotNull(capturedOrderInfo);
        assertEquals(STATUS_PENDING, capturedOrderInfo.getStatus(), "傳遞給 repository 的 OrderInfo 狀態應為處理中(1001)");
        assertEquals(1, capturedOrderInfo.getAccountId()); // 直接使用值

        // Verify interactions (optional but good practice)
        verify(accountClient, times(1)).validateActiveAccount(1); // 直接使用值
        verify(productClient, times(1)).getProductDetails(Set.of(1)); // 直接使用值
        verify(productClient, times(1)).updateProductsStock(anyMap());
        verify(orderInfoRepository, times(1)).save(any(OrderInfo.class)); // 驗證 save 被呼叫
        verify(orderDetailRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("建立訂單時，若帳號非活躍，應拋出 AccountInactiveException")
    void createOrder_WhenAccountIsInactive_ShouldThrowAccountInactiveException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(1); // 直接使用值
        request.setOrderDetails(List.of(new CreateOrderDetailRequest(1, 1))); // 即使有商品也不應處理, 直接使用值

        // 模擬 AccountClient 在驗證時拋出例外
        doThrow(new AccountInactiveException("Account " + 1 + " is inactive.")).when(accountClient) // 直接使用值
                .validateActiveAccount(1); // 直接使用值

        // Act & Assert
        AccountInactiveException exception = assertThrows(AccountInactiveException.class, () -> {
            orderService.createOrder(request);
        });

        assertEquals("Account " + 1 + " is inactive.", exception.getMessage()); // 直接使用值
        // Verify 確保沒有儲存任何訂單或明細
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("建立訂單時，若有商品不可銷售，應拋出 ProductInactiveException")
    void createOrder_WhenProductNotSellable_ShouldThrowProductInactiveException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(1); // 直接使用值
        request.setOrderDetails(List.of(
                new CreateOrderDetailRequest(1, 1), // Sellable, 直接使用值
                new CreateOrderDetailRequest(2, 1) // Not Sellable, 直接使用值
        ));

        doNothing().when(accountClient).validateActiveAccount(1); // 直接使用值

        // Mock Product Details: Product 2 is not sellable (status 1002)
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        GetProductDetailResponse sellableProduct = createTestProductDetailResponse(1, "P1", BigDecimal.TEN, 10); // 直接使用值
        GetProductDetailResponse notSellableProduct = createTestProductDetailResponse(2, "P2", BigDecimal.ONE, 5); // 直接使用值
        notSellableProduct.setSaleStatus(PRODUCT_STATUS_NOT_SELLABLE); // <-- 設定為不可銷售
        productDetailsMap.put(1, sellableProduct); // 直接使用值
        productDetailsMap.put(2, notSellableProduct); // 直接使用值

        // 模擬 ProductClient 在獲取商品詳情時，因商品不可銷售而拋出例外
        when(productClient.getProductDetails(Set.of(1, 2))) // 直接使用值
                .thenThrow(new ProductInactiveException("商品不可銷售: ID=" + 2)); // 模擬拋出例外, 直接使用值

        // Act & Assert
        ProductInactiveException exception = assertThrows(ProductInactiveException.class, () -> { // 預期
                                                                                                  // ProductInactiveException
            orderService.createOrder(request);
        });

        assertTrue(exception.getMessage().contains("商品不可銷售: ID=" + 2)); // 直接使用值

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
        request.setAccountId(1); // 直接使用值
        request.setOrderDetails(List.of(new CreateOrderDetailRequest(1, 15))); // Request 15, 直接使用值

        doNothing().when(accountClient).validateActiveAccount(1); // 直接使用值

        // Mock Product Details: Product 1 is sellable but only has 10 in stock
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(1, createTestProductDetailResponse(1, "P1", BigDecimal.TEN, 10)); // Stock is 10, 直接使用值
        when(productClient.getProductDetails(Set.of(1))).thenReturn(productDetailsMap); // 直接使用值

        // Act & Assert
        ProductStockNotEnoughException exception = assertThrows(ProductStockNotEnoughException.class, () -> {
            orderService.createOrder(request); // 這裡不需要改
        });

        // 驗證例外訊息符合 calculateNewStock 的格式
        String expectedMsgPart1 = String.format("商品 %d 庫存不足", 1);
        String expectedMsgPart2 = String.format("目前庫存: %d", 10); // 測試設定的庫存
        String expectedMsgPart3 = String.format("訂單新數量: %d", 15); // 測試請求的數量
        assertTrue(exception.getMessage().contains(expectedMsgPart1),
                "Exception message should contain product ID part");
        assertTrue(exception.getMessage().contains(expectedMsgPart2),
                "Exception message should contain current stock part");
        assertTrue(exception.getMessage().contains(expectedMsgPart3),
                "Exception message should contain requested quantity part");

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
        OrderInfo existingOrder = createTestOrderInfo(101, 1, STATUS_COMPLETED); // Status is NOT PENDING

        // 2. Mock findById to return this order
        when(orderInfoRepository.findById(101)).thenReturn(Optional.of(existingOrder)); // 直接使用值

        // 3. Prepare an update request (content doesn't matter much for this specific
        // test)
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(101); // 直接使用值
        request.setItems(List.of(new UpdateOrderDetailRequest(1, 1))); // Example item, 直接使用值

        // Act & Assert
        OrderStatusInvalidException exception = assertThrows(OrderStatusInvalidException.class, () -> {
            orderService.updateOrder(request);
        });

        assertTrue(exception.getMessage().contains("訂單狀態不允許更新商品項目")); // 訊息不變
        assertTrue(exception.getMessage().contains(String.valueOf(STATUS_COMPLETED)));

        // Verify findById was called, but no further processing (like getting product
        // details or saving) occurred
        verify(orderInfoRepository, times(1)).findById(101);
        verify(productClient, never()).getProductDetails(anySet());
        verify(productClient, never()).updateProductsStock(anyMap());
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(orderDetailRepository, never()).deleteAll(anyList());
    }

    // @Test
    // @DisplayName("更新訂單成功時，應正確處理新增、更新、移除的商品，並更新庫存")
    // void updateOrder_Success_HandlesAddUpdateRemoveCorrectly() { // 更名以反映測試目標
    //     // Arrange
    //     // 1. Create an existing order with PENDING status
    //     // 原訂單: 商品1 (ID=1, Qty=5), 商品2 (ID=2, Qty=3)
    //     OrderInfo existingOrder = createTestOrderInfo(101, 1, STATUS_PENDING); // Directly use constant
    //     // Add an existing detail
    //     createTestOrderDetail(existingOrder, 1, 5); // 直接使用值
    //     createTestOrderDetail(existingOrder, 2, 3); // 直接使用值

    //     // 2. Mock findById
    //     when(orderInfoRepository.findById(101)).thenReturn(Optional.of(existingOrder)); // 直接使用值

    //     // 3. Prepare update request (e.g., change quantity)
    //     UpdateOrderRequest request = new UpdateOrderRequest();
    //     request.setOrderId(101); // 直接使用值
    //     // 更新後訂單: 商品1 (ID=1, Qty=8 -> 更新), 商品3 (ID=3, Qty=2 -> 新增), 商品2被移除
    //     List<UpdateOrderDetailRequest> updatedItems = new ArrayList<>();
    //     updatedItems.add(new UpdateOrderDetailRequest(1, 8)); // Update quantity, 直接使用值
    //     updatedItems.add(new UpdateOrderDetailRequest(3, 2)); // Add new item, 直接使用值
    //     request.setItems(updatedItems);

    //     // 4. Mock Product Details (needed for update logic)
    //     // 假設商品1庫存100, 商品3庫存50
    //     // 假設商品2(被移除)庫存20
    //     Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
    //     productDetailsMap.put(1,
    //             createTestProductDetailResponse(1, "Product 1", new BigDecimal("10.00"), 100)); // 直接使用值
    //     productDetailsMap.put(3,
    //             createTestProductDetailResponse(3, "Product 3", new BigDecimal("20.00"), 50)); // 直接使用值
    //     productDetailsMap.put(2,
    //             createTestProductDetailResponse(2, "Product 2", new BigDecimal("15.00"), 20));
    //     // 需取得更新後訂單的所有商品 (ID=1, ID=3)
    //     // *** 修正: 實際呼叫會包含所有變動的商品ID (1, 2, 3) ***
    //     when(productClient.getProductDetails(Set.of(1, 2, 3))).thenReturn(productDetailsMap); // 直接使用值

    //     // 5. Mock stock update
    //     // 使用 ArgumentCaptor 捕獲傳遞給 updateProductsStock 的 Map
    //     ArgumentCaptor<Map<Integer, Integer>> stockUpdateCaptor = ArgumentCaptor.forClass(Map.class);
    //     doNothing().when(productClient).updateProductsStock(stockUpdateCaptor.capture());

    //     // 6. Mock OrderDetail save (for the updated detail)
    //     ArgumentCaptor<List<OrderDetail>> savedDetailsCaptor = ArgumentCaptor.forClass(List.class);
    //     when(orderDetailRepository.saveAll(savedDetailsCaptor.capture()))
    //             .thenAnswer(invocation -> invocation.getArgument(0));
    //     // Mock OrderDetail delete (for the removed detail)
    //     ArgumentCaptor<List<OrderDetail>> deletedDetailsCaptor = ArgumentCaptor.forClass(List.class);
    //     doNothing().when(orderDetailRepository).deleteAll(deletedDetailsCaptor.capture());

    //     // 7. Mock OrderInfo save
    //     when(orderInfoRepository.save(any(OrderInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

    //     // Act
    //     // We expect this *not* to throw OrderStatusInvalidException
    //     // We can assert the response or verify interactions to confirm processing
    //     // continued
    //     UpdateOrderResponse response = orderService.updateOrder(request);

    //     // Assert
    //     assertNotNull(response);
    //     assertEquals(101, response.getOrderId()); // 直接使用值
    //     // 驗證回傳的項目數量是否正確
    //     assertEquals(2, response.getItems().size());
    //     assertTrue(response.getItems().stream().anyMatch(item -> item.getProductId() == 1 && item.getQuantity() == 8));
    //     assertTrue(response.getItems().stream().anyMatch(item -> item.getProductId() == 3 && item.getQuantity() == 2));

    //     // Verify that processing continued beyond the status check
    //     verify(orderInfoRepository, times(1)).findById(101); // 直接使用值
    //     verify(productClient, times(1)).getProductDetails(Set.of(1, 2, 3)); // 驗證取得的是所有變動商品的詳情

    //     // 驗證庫存更新
    //     verify(productClient, times(1)).updateProductsStock(anyMap()); // 驗證庫存更新被呼叫
    //     Map<Integer, Integer> capturedStockUpdates = stockUpdateCaptor.getValue();
    //     assertNotNull(capturedStockUpdates);
    //     assertEquals(3, capturedStockUpdates.size(), "應包含所有變動商品的庫存更新 (1+2+3)");
    //     // 商品1: 原5 -> 新8, 庫存應減少 3 (100 - 3 = 97)
    //     assertEquals(97, capturedStockUpdates.get(1));
    //     // 商品2: 原3 -> 移除, 庫存應增加 3 (20 + 3 = 23)
    //     assertEquals(23, capturedStockUpdates.get(2));
    //     // 商品3: 新增2, 庫存應減少 2 (50 - 2 = 48)
    //     assertEquals(48, capturedStockUpdates.get(3));

    //     verify(orderDetailRepository, times(1)).deleteAll(deletedDetailsCaptor.capture()); // 驗證刪除商品2
    //     assertEquals(1, deletedDetailsCaptor.getValue().size());
    //     assertEquals(2, deletedDetailsCaptor.getValue().get(0).getProductId());
    //     verify(orderDetailRepository, times(1)).saveAll(savedDetailsCaptor.capture()); // 驗證儲存商品1和3
    //     assertEquals(2, savedDetailsCaptor.getValue().size());
    //     assertTrue(savedDetailsCaptor.getValue().stream().anyMatch(d -> d.getProductId() == 1 && d.getQuantity() == 8));
    //     assertTrue(savedDetailsCaptor.getValue().stream().anyMatch(d -> d.getProductId() == 3 && d.getQuantity() == 2));
    //     verify(orderInfoRepository, times(1)).save(any(OrderInfo.class)); // Called
    // }

    @Test
    @DisplayName("更新訂單時，若有商品不可銷售，應拋出 ProductInactiveException")
    void updateOrder_WhenProductNotSellable_ShouldThrowProductInactiveException() {
        // Arrange
        OrderInfo existingOrder = createTestOrderInfo(101, 1, STATUS_PENDING); // Directly use constant
        createTestOrderDetail(existingOrder, 1, 2); // Original detail, 直接使用值

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(101); // 直接使用值
        request.setItems(List.of(
                new UpdateOrderDetailRequest(1, 1), // Still sellable, 直接使用值
                new UpdateOrderDetailRequest(2, 1) // Add a non-sellable item, 直接使用值
        ));

        when(orderInfoRepository.findById(101)).thenReturn(Optional.of(existingOrder)); // 直接使用值

        // Mock Product Details: Product 2 is not sellable
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        GetProductDetailResponse sellableProduct = createTestProductDetailResponse(1, "P1", BigDecimal.TEN, 10); // 直接使用值
        GetProductDetailResponse notSellableProduct = createTestProductDetailResponse(2, "P2", BigDecimal.ONE, 5); // 直接使用值
        notSellableProduct.setSaleStatus(PRODUCT_STATUS_NOT_SELLABLE); // <-- 設定為不可銷售
        productDetailsMap.put(1, sellableProduct); // 直接使用值
        productDetailsMap.put(2, notSellableProduct); // 直接使用值

        // 模擬 ProductClient 在獲取商品詳情時，因商品不可銷售而拋出例外
        when(productClient.getProductDetails(Set.of(1, 2))) // 直接使用值
                .thenThrow(new ProductInactiveException("商品不可銷售: ID=" + 2)); // 模擬拋出例外, 直接使用值

        // Act & Assert
        ProductInactiveException exception = assertThrows(ProductInactiveException.class, () -> { // 預期
                                                                                                  // ProductInactiveException
            orderService.updateOrder(request);
        });

        assertTrue(exception.getMessage().contains("商品不可銷售: ID=" + 2)); // 直接使用值

        // Verify no persistence occurred beyond finding the initial order
        verify(orderInfoRepository, times(1)).findById(101); // 直接使用值
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(orderDetailRepository, never()).deleteAll(anyList());
        verify(productClient, never()).updateProductsStock(anyMap());
    }

    @Test
    @DisplayName("更新訂單時，若有商品庫存不足，應拋出 ProductStockNotEnoughException")
    void updateOrder_WhenInsufficientStock_ShouldThrowProductStockNotEnoughException() { // 修正方法名中的例外類型
        // Arrange
        OrderInfo existingOrder = createTestOrderInfo(101, 1, STATUS_PENDING); // Directly use constant
        createTestOrderDetail(existingOrder, 1, 2); // Original detail, 直接使用值

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(101); // 直接使用值
        request.setItems(List.of(new UpdateOrderDetailRequest(1, 15))); // Request 15, 直接使用值

        when(orderInfoRepository.findById(101)).thenReturn(Optional.of(existingOrder)); // 直接使用值

        // Mock Product Details: Product 1 is sellable but only has 10 in stock
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(1, createTestProductDetailResponse(1, "P1", BigDecimal.TEN, 10)); // Stock is 10, 直接使用值
        when(productClient.getProductDetails(Set.of(1))).thenReturn(productDetailsMap); // 直接使用值

        // Act & Assert
        ProductStockNotEnoughException exception = assertThrows(ProductStockNotEnoughException.class, () -> {
            orderService.updateOrder(request);
        });

        // 驗證例外訊息符合 calculateNewStock 的格式
        String expectedMsgPart1 = String.format("商品 %d 庫存不足", 1); // 直接使用值
        String expectedMsgPart2 = String.format("目前庫存: %d", 10); // 測試設定的庫存
        String expectedMsgPart3 = String.format("訂單原數量: %d", 2); // 測試中訂單原數量
        String expectedMsgPart4 = String.format("訂單新數量: %d", 15); // 測試請求的數量
        assertTrue(exception.getMessage().contains(expectedMsgPart1),
                "Exception message should contain product ID part");
        assertTrue(exception.getMessage().contains(expectedMsgPart2),
                "Exception message should contain current stock part");
        assertTrue(exception.getMessage().contains(expectedMsgPart3),
                "Exception message should contain original quantity part");
        assertTrue(exception.getMessage().contains(expectedMsgPart4),
                "Exception message should contain requested quantity part");

        // Verify no persistence occurred beyond finding the initial order
        verify(orderInfoRepository, times(1)).findById(101); // 直接使用值
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
        OrderInfo existingOrder = createTestOrderInfo(101, 1, STATUS_DELETED); // Status is NOT PENDING

        // 2. Mock findById
        when(orderInfoRepository.findById(101)).thenReturn(Optional.of(existingOrder)); // 直接使用值

        // Act & Assert
        OrderStatusInvalidException exception = assertThrows(OrderStatusInvalidException.class, () -> {
            orderService.deleteOrder(101); // 直接使用值
        });

        assertTrue(exception.getMessage().contains("訂單狀態不允許刪除")); // 訊息不變
        assertTrue(exception.getMessage().contains(String.valueOf(STATUS_DELETED)));

        // Verify findById was called, but no further processing occurred
        verify(orderInfoRepository, times(1)).findById(101); // 直接使用值
        verify(productClient, never()).getProductDetails(anySet());
        verify(productClient, never()).updateProductsStock(anyMap());
        verify(orderInfoRepository, never()).save(any(OrderInfo.class)); // Save should not be called
    }

    @Test
    @DisplayName("刪除訂單成功時，訂單狀態應更新為已刪除(1003)")
    void deleteOrder_WhenStatusIsPending_ShouldUpdateStatusToDeleted() {
        // Arrange
        // 1. Create an existing order with PENDING status
        OrderInfo existingOrder = createTestOrderInfo(101, 1, STATUS_PENDING); // Directly use constant
        // Add some details to simulate restoring stock
        createTestOrderDetail(existingOrder, 1, 3); // 直接使用值
        createTestOrderDetail(existingOrder, 2, 2); // 直接使用值

        // 2. Mock findById
        when(orderInfoRepository.findById(101)).thenReturn(Optional.of(existingOrder)); // 直接使用值

        // 3. Mock Product Details (needed for stock restoration)
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(1,
                createTestProductDetailResponse(1, "P1", new BigDecimal("10"), 50)); // 直接使用值
        productDetailsMap.put(2,
                createTestProductDetailResponse(2, "P2", new BigDecimal("20"), 30)); // 直接使用值
        when(productClient.getProductDetails(Set.of(1, 2))) // 直接使用值
                .thenReturn(productDetailsMap);

        // 4. Mock stock update
        doNothing().when(productClient).updateProductsStock(anyMap());

        // 5. Mock OrderInfo save
        ArgumentCaptor<OrderInfo> orderInfoCaptor = ArgumentCaptor.forClass(OrderInfo.class);
        when(orderInfoRepository.save(orderInfoCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        orderService.deleteOrder(101); // 直接使用值

        // Assert
        // Verify the status of the OrderInfo object passed to save
        OrderInfo capturedOrderInfo = orderInfoCaptor.getValue();
        assertNotNull(capturedOrderInfo);
        assertEquals(STATUS_DELETED, capturedOrderInfo.getStatus(), "訂單狀態應更新為已刪除(1003)");
        assertEquals(101, capturedOrderInfo.getId()); // Ensure it's the correct order, 直接使用值

        // Verify interactions
        verify(orderInfoRepository, times(1)).findById(101); // 直接使用值
        verify(productClient, times(1)).getProductDetails(Set.of(1, 2)); // 直接使用值
        verify(productClient, times(1)).updateProductsStock(anyMap());
        verify(orderInfoRepository, times(1)).save(any(OrderInfo.class)); // Verify save was called

    }

    // --- Tests for getOrderList ---

    // @Test
    // @DisplayName("取得訂單列表成功，應回傳該帳戶的訂單列表及總金額")
    // void getOrderList_Success_ShouldReturnOrderList() {
    //     // Arrange
    //     Integer accountId = 1;
    //     doNothing().when(accountClient).validateActiveAccount(accountId);

    //     // Create two orders for the account
    //     OrderInfo order1 = createTestOrderInfo(101, accountId, STATUS_PENDING);
    //     OrderDetail detail1_1 = createTestOrderDetail(order1, 1, 2); // P1, Qty 2
    //     OrderDetail detail1_2 = createTestOrderDetail(order1, 2, 1); // P2, Qty 1

    //     OrderInfo order2 = createTestOrderInfo(102, accountId, STATUS_COMPLETED);
    //     OrderDetail detail2_1 = createTestOrderDetail(order2, 1, 3); // P1, Qty 3

    //     List<OrderInfo> orders = List.of(order1, order2);
    //     when(orderInfoRepository.findByAccountId(accountId)).thenReturn(orders);

    //     // Mock product details needed for total amount calculation
    //     Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
    //     productDetailsMap.put(1, createTestProductDetailResponse(1, "P1", new BigDecimal("10.00"), 50));
    //     productDetailsMap.put(2, createTestProductDetailResponse(2, "P2", new BigDecimal("25.50"), 30));
    //     when(productClient.getProductDetails(Set.of(1, 2))).thenReturn(productDetailsMap); // For order1
    //     when(productClient.getProductDetails(Set.of(1))).thenReturn(Map.of(1, productDetailsMap.get(1))); // For order2

    //     // Act
    //     List<GetOrderListResponse> responseList = orderService.getOrderList(accountId);

    //     // Assert
    //     assertNotNull(responseList);
    //     assertEquals(2, responseList.size());

    //     // Check Order 1
    //     GetOrderListResponse res1 = responseList.stream().filter(r -> r.getOrderId() == 101).findFirst().orElseThrow();
    //     assertEquals(STATUS_PENDING, res1.getStatus());
    //     // Expected Total for Order 1: (2 * 10.00) + (1 * 25.50) = 20.00 + 25.50 = 45.50
    //     assertEquals(new BigDecimal("45.50"), res1.getTotalAmount());

    //     // Check Order 2
    //     GetOrderListResponse res2 = responseList.stream().filter(r -> r.getOrderId() == 102).findFirst().orElseThrow();
    //     assertEquals(STATUS_COMPLETED, res2.getStatus());
    //     // Expected Total for Order 2: (3 * 10.00) = 30.00
    //     assertEquals(new BigDecimal("30.00"), res2.getTotalAmount());

    //     verify(accountClient, times(1)).validateActiveAccount(accountId);
    //     verify(orderInfoRepository, times(1)).findByAccountId(accountId);
    //     verify(productClient, times(2)).getProductDetails(anySet()); // Called once per order
    // }

    // @Test
    // @DisplayName("取得訂單列表時，若帳戶無訂單，應回傳空列表")
    // void getOrderList_WhenNoOrders_ShouldReturnEmptyList() {
    //     // Arrange
    //     Integer accountId = 2;
    //     doNothing().when(accountClient).validateActiveAccount(accountId);
    //     when(orderInfoRepository.findByAccountId(accountId)).thenReturn(Collections.emptyList());

    //     // Act
    //     List<GetOrderListResponse> responseList = orderService.getOrderList(accountId);

    //     // Assert
    //     assertNotNull(responseList);
    //     assertTrue(responseList.isEmpty());

    //     verify(accountClient, times(1)).validateActiveAccount(accountId);
    //     verify(orderInfoRepository, times(1)).findByAccountId(accountId);
    //     verify(productClient, never()).getProductDetails(anySet()); // No orders, no need to get product details
    // }

    @Test
    @DisplayName("取得訂單列表時，若帳戶非活躍，應拋出 AccountInactiveException")
    void getOrderList_WhenAccountInactive_ShouldThrowAccountInactiveException() {
        // Arrange
        Integer accountId = 3;
        doThrow(new AccountInactiveException("Account " + accountId + " is inactive."))
                .when(accountClient).validateActiveAccount(accountId);

        // Act & Assert
        AccountInactiveException exception = assertThrows(AccountInactiveException.class, () -> {
            orderService.getOrderList(accountId);
        });
        assertTrue(exception.getMessage().contains("is inactive"));

        verify(accountClient, times(1)).validateActiveAccount(accountId);
        verify(orderInfoRepository, never()).findByAccountId(anyInt());
        verify(productClient, never()).getProductDetails(anySet());
    }

    // --- Tests for getOrderDetails ---

    // @Test
    // @DisplayName("取得訂單詳情成功，應回傳包含商品資訊的完整明細")
    // void getOrderDetails_Success_ShouldReturnFullDetails() {
    //     // Arrange
    //     Integer orderId = 101;
    //     Integer accountId = 1;
    //     OrderInfo order = createTestOrderInfo(orderId, accountId, STATUS_PENDING);
    //     OrderDetail detail1 = createTestOrderDetail(order, 1, 2); // P1, Qty 2
    //     OrderDetail detail2 = createTestOrderDetail(order, 2, 1); // P2, Qty 1

    //     when(orderInfoRepository.findById(orderId)).thenReturn(Optional.of(order));

    //     // Mock product details
    //     Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
    //     productDetailsMap.put(1, createTestProductDetailResponse(1, "Product A", new BigDecimal("15.00"), 50));
    //     productDetailsMap.put(2, createTestProductDetailResponse(2, "Product B", new BigDecimal("30.00"), 30));
    //     when(productClient.getProductDetails(Set.of(1, 2))).thenReturn(productDetailsMap);

    //     // Act
    //     GetOrderDetailResponse response = orderService.getOrderDetails(orderId);

    //     // Assert
    //     assertNotNull(response);
    //     assertEquals(accountId, response.getAccountId());
    //     assertEquals(STATUS_PENDING, response.getOrderStatus());
    //     // Expected Total: (2 * 15.00) + (1 * 30.00) = 30.00 + 30.00 = 60.00
    //     assertEquals(new BigDecimal("60.00"), response.getTotalAmount());
    //     assertEquals(2, response.getItems().size());

    //     // Check Item 1
    //     OrderItemDTO item1 = response.getItems().stream().filter(i -> i.getProductId() == 1).findFirst().orElseThrow();
    //     assertEquals("Product A", item1.getProductName());
    //     assertEquals(2, item1.getQuantity());
    //     assertEquals(new BigDecimal("15.00"), item1.getProductPrice());

    //     // Check Item 2
    //     OrderItemDTO item2 = response.getItems().stream().filter(i -> i.getProductId() == 2).findFirst().orElseThrow();
    //     assertEquals("Product B", item2.getProductName());
    //     assertEquals(1, item2.getQuantity());
    //     assertEquals(new BigDecimal("30.00"), item2.getProductPrice());

    //     verify(orderInfoRepository, times(1)).findById(orderId);
    //     verify(productClient, times(2)).getProductDetails(Set.of(1, 2)); // Called once for details, once for total
    // }

    // @Test
    // @DisplayName("取得訂單詳情時，若訂單不存在，應拋出 ResourceNotFoundException")
    // void getOrderDetails_WhenOrderNotFound_ShouldThrowResourceNotFoundException() {
    //     // Arrange
    //     Integer orderId = 999;
    //     when(orderInfoRepository.findById(orderId)).thenReturn(Optional.empty());

    //     // Act & Assert
    //     ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
    //         orderService.getOrderDetails(orderId);
    //     });
    //     assertTrue(exception.getMessage().contains("Order not found with id: " + orderId));

    //     verify(orderInfoRepository, times(1)).findById(orderId);
    //     verify(productClient, never()).getProductDetails(anySet());
    // }

    // --- Tests for AccountIdIsInOrder ---

    // @Test
    // @DisplayName("檢查帳戶是否有訂單時，若有訂單，應回傳 true")
    // void accountIdIsInOrder_WhenOrdersExist_ShouldReturnTrue() {
    //     // Arrange
    //     Integer accountId = 1;
    //     when(orderInfoRepository.findByAccountId(accountId)).thenReturn(List.of(new OrderInfo())); // Return non-empty
    //                                                                                                // list

    //     // Act
    //     boolean result = orderService.AccountIdIsInOrder(accountId);

    //     // Assert
    //     assertTrue(result);
    //     verify(orderInfoRepository, times(1)).findByAccountId(accountId);
    // }

    // @Test
    // @DisplayName("檢查帳戶是否有訂單時，若無訂單，應回傳 false")
    // void accountIdIsInOrder_WhenNoOrdersExist_ShouldReturnFalse() {
    //     // Arrange
    //     Integer accountId = 2;
    //     when(orderInfoRepository.findByAccountId(accountId)).thenReturn(Collections.emptyList()); // Return empty list

    //     // Act
    //     boolean result = orderService.AccountIdIsInOrder(accountId);

    //     // Assert
    //     assertFalse(result);
    //     verify(orderInfoRepository, times(1)).findByAccountId(accountId);
    // }
}
