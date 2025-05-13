package com.ibm.demo.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.AccountClient;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductStockNotEnoughException;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
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
    // private static final int STATUS_COMPLETED = 1002; // Assuming 1002 is
    // Completed
    private static final int STATUS_DELETED = 1003;

    // --- Constants for Product Status ---
    private static final int PRODUCT_STATUS_SELLABLE = 1001;
    private static final int PRODUCT_STATUS_NOT_SELLABLE = 1002;

    // --- Constants for Test Data ---
    private static final Integer ACTIVE_ACCOUNT_ID = 1;
    private static final Integer INACTIVE_ACCOUNT_ID = 2; // For testing inactive account scenarios
    private static final Integer SELLABLE_PRODUCT_ID_1 = 1;
    private static final Integer NON_SELLABLE_PRODUCT_ID = 2;
    private static final Integer PRODUCT_ID_FOR_STOCK_CHECK = 3;
    private static final Integer EXISTING_ORDER_ID = 101;

    private static final BigDecimal DEFAULT_PRICE = BigDecimal.TEN;
    private static final Integer DEFAULT_STOCK_QTY = 10;
    private static final Integer LOW_STOCK_QTY = 5; // For product used in stock checks
    private static final Integer REQUEST_QTY_EXCEEDING_STOCK = 15;
    private static final Integer DEFAULT_ORDER_ITEM_QTY = 2;

    // --- Shared Mock Responses ---
    private GetAccountDetailResponse activeAccountResponse;
    private GetAccountDetailResponse inactiveAccountResponse;
    private GetProductDetailResponse sellableProduct1Detail;
    private GetProductDetailResponse nonSellableProductDetail;
    private GetProductDetailResponse productForStockCheckDetail;

    @BeforeEach
    void setUp() {
        activeAccountResponse = createTestAccountDetailResponse(ACTIVE_ACCOUNT_ID, "Y");
        inactiveAccountResponse = createTestAccountDetailResponse(INACTIVE_ACCOUNT_ID, "N");

        sellableProduct1Detail = createTestProductDetailResponse(SELLABLE_PRODUCT_ID_1, "Sellable Product 1", DEFAULT_PRICE, DEFAULT_STOCK_QTY);
        nonSellableProductDetail = createTestProductDetailResponse(NON_SELLABLE_PRODUCT_ID, "Non-Sellable Product", DEFAULT_PRICE, DEFAULT_STOCK_QTY);
        nonSellableProductDetail.setSaleStatus(PRODUCT_STATUS_NOT_SELLABLE);
        productForStockCheckDetail = createTestProductDetailResponse(PRODUCT_ID_FOR_STOCK_CHECK, "Low Stock Product", DEFAULT_PRICE, LOW_STOCK_QTY);
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

    private GetAccountDetailResponse createTestAccountDetailResponse(Integer accountId, String status) {
        GetAccountDetailResponse accResponse = new GetAccountDetailResponse();
        accResponse.setStatus(status);
        return accResponse;
    }

    @Test
    @DisplayName("建立訂單時，若帳號非活躍，應拋出 AccountInactiveException")
    void createOrder_WhenAccountIsInactive_ShouldThrowAccountInactiveException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(INACTIVE_ACCOUNT_ID);
        request.setOrderDetails(List.of(new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID_1, 1)));

        // 模擬 accountClient.getAccountDetail 返回一個狀態為 "N" (非活躍) 的帳戶
        when(accountClient.getAccountDetail(INACTIVE_ACCOUNT_ID)).thenReturn(this.inactiveAccountResponse);

        // Act & Assert
        AccountInactiveException exception = assertThrows(AccountInactiveException.class, () -> {
            orderService.createOrder(request);
        });

        assertEquals("帳戶狀態停用", exception.getMessage());
        // Verify 確保沒有儲存任何訂單或明細
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("建立訂單時，若有商品不可銷售，應拋出 ProductInactiveException")
    void createOrder_WhenProductNotSellable_ShouldThrowProductInactiveException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAccountId(ACTIVE_ACCOUNT_ID);
        request.setOrderDetails(List.of(
                new CreateOrderDetailRequest(SELLABLE_PRODUCT_ID_1, 1),
                new CreateOrderDetailRequest(NON_SELLABLE_PRODUCT_ID, 1)
        ));

        // 模擬 accountClient.getAccountDetail 返回一個活躍帳戶
        when(accountClient.getAccountDetail(ACTIVE_ACCOUNT_ID)).thenReturn(this.activeAccountResponse);

        // Mock Product Details: NON_SELLABLE_PRODUCT_ID is not sellable
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(SELLABLE_PRODUCT_ID_1, sellableProduct1Detail);
        productDetailsMap.put(NON_SELLABLE_PRODUCT_ID, nonSellableProductDetail); // This one has status NOT_SELLABLE

        // The service's batchGetProductDetailsIfInactiveThrow method will perform the check
        when(productClient.getProductDetails(Set.of(SELLABLE_PRODUCT_ID_1, NON_SELLABLE_PRODUCT_ID)))
                .thenReturn(productDetailsMap);

        // Act & Assert
        ProductInactiveException exception = assertThrows(ProductInactiveException.class, () -> {
            orderService.createOrder(request);
        });
        // The service method batchGetProductDetailsIfInactiveThrow throws "商品不可銷售，ID: " + productId
        assertTrue(exception.getMessage().contains("商品不可銷售，ID: " + NON_SELLABLE_PRODUCT_ID));

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
        request.setAccountId(ACTIVE_ACCOUNT_ID);
        request.setOrderDetails(List.of(new CreateOrderDetailRequest(PRODUCT_ID_FOR_STOCK_CHECK, REQUEST_QTY_EXCEEDING_STOCK)));

        // 模擬 accountClient.getAccountDetail 返回一個活躍帳戶
        when(accountClient.getAccountDetail(ACTIVE_ACCOUNT_ID)).thenReturn(this.activeAccountResponse);

        // Mock Product Details: PRODUCT_ID_FOR_STOCK_CHECK has LOW_STOCK_QTY
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(PRODUCT_ID_FOR_STOCK_CHECK, productForStockCheckDetail);
        when(productClient.getProductDetails(Set.of(PRODUCT_ID_FOR_STOCK_CHECK))).thenReturn(productDetailsMap);

        // Act & Assert
        ProductStockNotEnoughException exception = assertThrows(ProductStockNotEnoughException.class, () -> {
            orderService.createOrder(request);
        });

        // Verify exception message based on calculateNewStock format
        String expectedMsgPart1 = String.format("商品 %d 庫存不足", PRODUCT_ID_FOR_STOCK_CHECK);
        String expectedMsgPart2 = String.format("目前庫存: %d", LOW_STOCK_QTY);
        String expectedMsgPart3 = String.format("訂單新數量: %d", REQUEST_QTY_EXCEEDING_STOCK);
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
    @DisplayName("更新訂單時，若訂單不存在，應拋出 ResourceNotFoundException")
    void updateOrder_WhenOrderNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(EXISTING_ORDER_ID);
        request.setItems(List.of(new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID_1, 1)));

        // Mock findById to return empty
        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            orderService.updateOrder(request);
        });

        assertEquals("Order not found with ID: " + EXISTING_ORDER_ID, exception.getMessage());

        // Verify no persistence occurred
        verify(orderInfoRepository, times(1)).findById(EXISTING_ORDER_ID);
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("更新訂單時，若有商品不可銷售，應拋出 ProductInactiveException")
    void updateOrder_WhenProductNotSellable_ShouldThrowProductInactiveException() {
        // Arrange
        OrderInfo existingOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, STATUS_PENDING);
        createTestOrderDetail(existingOrder, SELLABLE_PRODUCT_ID_1, DEFAULT_ORDER_ITEM_QTY); // Original detail

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(EXISTING_ORDER_ID);
        request.setItems(List.of(
                new UpdateOrderDetailRequest(SELLABLE_PRODUCT_ID_1, 1), // Update existing
                new UpdateOrderDetailRequest(NON_SELLABLE_PRODUCT_ID, 1) // Add a non-sellable item
        ));

        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // Mock Product Details: NON_SELLABLE_PRODUCT_ID is not sellable
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(SELLABLE_PRODUCT_ID_1, sellableProduct1Detail);
        productDetailsMap.put(NON_SELLABLE_PRODUCT_ID, nonSellableProductDetail); // This one has status NOT_SELLABLE

        when(productClient.getProductDetails(Set.of(SELLABLE_PRODUCT_ID_1, NON_SELLABLE_PRODUCT_ID)))
                .thenReturn(productDetailsMap);

        // Act & Assert
        ProductInactiveException exception = assertThrows(ProductInactiveException.class, () -> {
            orderService.updateOrder(request);
        });
        assertTrue(exception.getMessage().contains("商品不可銷售，ID: " + NON_SELLABLE_PRODUCT_ID));

        // Verify no persistence occurred beyond finding the initial order
        verify(orderInfoRepository, times(1)).findById(EXISTING_ORDER_ID);
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(orderDetailRepository, never()).deleteAll(anyList());
        verify(productClient, never()).updateProductsStock(anyMap());
    }

    @Test
    @DisplayName("更新訂單時，若有商品庫存不足，應拋出 ProductStockNotEnoughException")
    void updateOrder_WhenInsufficientStock_ShouldThrowProductStockNotEnoughException() { // 修正方法名中的例外類型
        // Arrange
        OrderInfo existingOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, STATUS_PENDING);
        createTestOrderDetail(existingOrder, PRODUCT_ID_FOR_STOCK_CHECK, DEFAULT_ORDER_ITEM_QTY); // Original detail

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderId(EXISTING_ORDER_ID);
        // Update item to a quantity that exceeds available stock
        request.setItems(List.of(new UpdateOrderDetailRequest(PRODUCT_ID_FOR_STOCK_CHECK, REQUEST_QTY_EXCEEDING_STOCK)));

        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // Mock Product Details: PRODUCT_ID_FOR_STOCK_CHECK has LOW_STOCK_QTY
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        productDetailsMap.put(PRODUCT_ID_FOR_STOCK_CHECK, productForStockCheckDetail);
        when(productClient.getProductDetails(Set.of(PRODUCT_ID_FOR_STOCK_CHECK))).thenReturn(productDetailsMap);

        // Act & Assert
        ProductStockNotEnoughException exception = assertThrows(ProductStockNotEnoughException.class, () -> {
            orderService.updateOrder(request);
        });

        // Verify exception message based on calculateNewStock format
        String expectedMsgPart1 = String.format("商品 %d 庫存不足", PRODUCT_ID_FOR_STOCK_CHECK);
        String expectedMsgPart2 = String.format("目前庫存: %d", LOW_STOCK_QTY);
        String expectedMsgPart3 = String.format("訂單原數量: %d", DEFAULT_ORDER_ITEM_QTY);
        String expectedMsgPart4 = String.format("訂單新數量: %d", REQUEST_QTY_EXCEEDING_STOCK);
        assertTrue(exception.getMessage().contains(expectedMsgPart1),
                "Exception message should contain product ID part");
        assertTrue(exception.getMessage().contains(expectedMsgPart2),
                "Exception message should contain current stock part");
        assertTrue(exception.getMessage().contains(expectedMsgPart3),
                "Exception message should contain original quantity part");
        assertTrue(exception.getMessage().contains(expectedMsgPart4),
                "Exception message should contain requested quantity part");

        // Verify no persistence occurred beyond finding the initial order
        verify(orderInfoRepository, times(1)).findById(EXISTING_ORDER_ID);
        verify(orderInfoRepository, never()).save(any(OrderInfo.class));
        verify(orderDetailRepository, never()).saveAll(anyList());
        verify(orderDetailRepository, never()).deleteAll(anyList());
        verify(productClient, never()).updateProductsStock(anyMap());
    }
    
    @Test
    @DisplayName("刪除訂單時，若訂單不存在，應拋出 ResourceNotFoundException")
    void deleteOrder_WhenOrderNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            orderService.deleteOrder(EXISTING_ORDER_ID);
        });

        assertEquals("Order not found with ID: " + EXISTING_ORDER_ID, exception.getMessage());

        // Verify findById was called, but no further processing occurred
        verify(orderInfoRepository, times(1)).findById(EXISTING_ORDER_ID);
        verify(productClient, never()).getProductDetails(anySet());
        verify(productClient, never()).updateProductsStock(anyMap());
        verify(orderInfoRepository, never()).save(any(OrderInfo.class)); // Save should not be called
    }

    @Test
    @DisplayName("刪除訂單時，若訂單狀態非處理中，應拋出 ResourceNotFoundException")
    void deleteOrder_WhenStatusNotPending_ShouldThrowResourceNotFoundException() {
        // Arrange
        // 1. Create an existing order with a non-pending
        OrderInfo existingOrder = createTestOrderInfo(EXISTING_ORDER_ID, ACTIVE_ACCOUNT_ID, STATUS_DELETED); // Status is DELETED

        // 2. Mock findById
        when(orderInfoRepository.findById(EXISTING_ORDER_ID)).thenReturn(Optional.of(existingOrder));

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            orderService.deleteOrder(EXISTING_ORDER_ID);
        });

        assertEquals("Order not found with ID: " + EXISTING_ORDER_ID, exception.getMessage());

        // Verify findById was called, but no further processing occurred
        verify(orderInfoRepository, times(1)).findById(EXISTING_ORDER_ID);
        verify(productClient, never()).getProductDetails(anySet());
        verify(productClient, never()).updateProductsStock(anyMap());
        verify(orderInfoRepository, never()).save(any(OrderInfo.class)); // Save should not be called
    }
}