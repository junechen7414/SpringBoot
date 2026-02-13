package com.ibm.demo.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ibm.demo.account.AccountClient;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.OrderStatusInvalidException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductStockNotEnoughException;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.DTO.OrderItemDTO;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.Entity.OrderDetail;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.DTO.GetProductDetailResponse;

import jakarta.transaction.Transactional;

@Service
public class OrderService {
        // private static final Logger logger =
        // LoggerFactory.getLogger(OrderService.class);
        private OrderInfoRepository orderInfoRepository;
        private OrderDetailRepository orderDetailRepository;
        private final AccountClient accountClient;
        private final ProductClient productClient;

        public OrderService(OrderInfoRepository orderInfoRepository,
                        OrderDetailRepository orderDetailRepository,
                        AccountClient accountClient,
                        ProductClient productClient) {
                this.orderInfoRepository = orderInfoRepository;
                this.orderDetailRepository = orderDetailRepository;
                this.accountClient = accountClient;
                this.productClient = productClient;
        }

        /**
         * @param createOrderRequest
         */
        @Transactional
        public Integer createOrder(CreateOrderRequest createOrderRequest) {
                // 驗證帳戶存在且狀態為啟用
                Integer accountId = createOrderRequest.getAccountId();
                validateActiveAccountOrThrow(accountId);

                // 收集商品ID並批取商品資訊
                Set<Integer> productIds = createOrderRequest.getOrderDetails().stream()
                                .map(CreateOrderDetailRequest::getProductId)
                                .collect(Collectors.toSet());
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(
                                productIds);

                // 建立新訂單
                OrderInfo newOrderInfo = OrderInfo.builder()
                                .accountId(accountId)
                                .status(1001)
                                .build();

                // 處理訂單明細與庫存更新
                var result = processOrderDetails(createOrderRequest, newOrderInfo, productDetailsMap);

                // 更新庫存並儲存訂單
                batchUpdateProductStock(result.stockUpdates());
                OrderInfo savedOrderInfo = orderInfoRepository.save(newOrderInfo);
                orderDetailRepository.saveAll(result.orderDetails());
                return savedOrderInfo.getId();
        }

        public List<GetOrderListResponse> getOrderListByAccountId(Integer accountId) {
                List<OrderInfo> orderInfoList = orderInfoRepository.findByAccountId(accountId);
                if (orderInfoList == null || orderInfoList.isEmpty()) {
                        return new ArrayList<>();
                }
                return orderInfoList.stream()
                                .map(orderInfo -> {
                                        GetOrderListResponse response = new GetOrderListResponse();
                                        response.setOrderId(orderInfo.getId());
                                        response.setStatus(orderInfo.getStatus());
                                        response.setTotalAmount(calculateOrderTotalAmount(orderInfo));
                                        return response;
                                }).collect(Collectors.toList());

        }

        /**
         * @param orderId
         * @return GetOrderDetailResponse
         */
        public GetOrderDetailResponse getOrderDetailByOrderId(Integer orderId) {
                // 1. 獲取訂單主檔（找不到直接噴 404）
                OrderInfo orderInfo = findByOrderIdOrThrow(orderId);
                List<OrderDetail> details = orderInfo.getOrderDetails();

                // 2. 批量獲取商品資訊（先收集 ID 再一次查詢，避免 N+1 問題）
                Set<Integer> productIds = details.stream()
                                .map(OrderDetail::getProductId)
                                .collect(Collectors.toSet());

                Map<Integer, GetProductDetailResponse> productMap = batchGetProductDetails(productIds);

                // 3. 組裝 DTO (使用 Stream 讓轉換過程一目了然)
                List<OrderItemDTO> itemDTOs = details.stream()
                                .map(detail -> {
                                        GetProductDetailResponse product = productMap.get(detail.getProductId());
                                        OrderItemDTO item = new OrderItemDTO();
                                        item.setProductId(detail.getProductId());
                                        item.setProductName(product.getName());
                                        item.setQuantity(detail.getQuantity());
                                        item.setProductPrice(product.getPrice());
                                        return item;
                                })
                                .collect(Collectors.toList());

                // 4. 回傳結果
                GetOrderDetailResponse response = new GetOrderDetailResponse();
                response.setAccountId(orderInfo.getAccountId());
                response.setOrderStatus(orderInfo.getStatus());
                response.setTotalAmount(calculateOrderTotalAmount(orderInfo));
                response.setItems(itemDTOs);

                return response;
        }

        /**
         * @param updateOrderRequest
         * @return UpdateOrderResponse
         */
        @Transactional
        public void updateOrder(UpdateOrderRequest request) {
                // 1. 獲取現有訂單
                OrderInfo order = findByOrderIdOrThrow(request.getOrderId());

                // 2. 準備 Map 以便比對
                Map<Integer, OrderDetail> existingMap = order.getOrderDetails().stream()
                                .collect(Collectors.toMap(OrderDetail::getProductId, Function.identity()));
                Map<Integer, UpdateOrderDetailRequest> incomingMap = request.getItems().stream()
                                .collect(Collectors.toMap(UpdateOrderDetailRequest::getProductId, Function.identity()));

                // 3. 計算庫存 Delta (實務建議：計算「異動量」而非直接算「新庫存」，這對併發處理較友善)
                // 但根據你的 batchUpdateProductStock 邏輯，我們維持計算最終 stock 的做法
                Map<Integer, Integer> stockUpdates = calculateStockUpdates(existingMap, incomingMap);

                // 4. 同步 Entity 集合 (解決 ObjectDeletedException 的核心)
                // 4A. 遍歷"現有"：辨別更新和刪除
                // 4B. 執行刪除（依賴 Orphan Removal）
                // 4C. 遍歷"新請求"：辨別新增
                // 4A. 分類：哪些要刪除、哪些要更新
                List<OrderDetail> detailsToDelete = new ArrayList<>();

                for (OrderDetail detail : order.getOrderDetails()) {
                        UpdateOrderDetailRequest incoming = incomingMap.get(detail.getProductId());
                        if (incoming == null) {
                                // 該商品在新請求中不存在，標記為刪除
                                detailsToDelete.add(detail);
                        } else {
                                // 該商品存在，更新數量
                                detail.setQuantity(incoming.getQuantity());
                        }
                }

                // 4B. 從內存集合中刪除 (Orphan Removal 會在 save 時自動刪除資料庫記錄)
                order.getOrderDetails().removeAll(detailsToDelete);

                // 4C. 處理新增項目
                for (Map.Entry<Integer, UpdateOrderDetailRequest> entry : incomingMap.entrySet()) {
                        Integer productId = entry.getKey();
                        UpdateOrderDetailRequest item = entry.getValue();

                        if (!existingMap.containsKey(productId)) {
                                // 新商品，加入訂單
                                order.getOrderDetails().add(
                                        OrderDetail.builder()
                                        .orderInfo(order) // 關聯到新訂單
                                        .productId(productId)
                                        .quantity(item.getQuantity())
                                        .build());
                        }
                }

                // 5. 執行遠端/批量庫存更新
                batchUpdateProductStock(stockUpdates);

                // 6. 更新訂單狀態並存檔
                order.setStatus(request.getOrderStatus());
                orderInfoRepository.save(order);
        }

        /**
         * @param orderId
         */
        @Transactional
        public void deleteOrder(Integer orderId) {
                // 1. 獲取訂單資訊
                OrderInfo existingOrderInfo = orderInfoRepository.findById(orderId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Order not found with ID: " + orderId));

                // 2. 驗證訂單狀態
                if (existingOrderInfo.getStatus() == 1003) {
                        throw new ResourceNotFoundException("Order not found with ID: " + orderId);
                }
                if (existingOrderInfo.getStatus() != 1001) {
                        throw new OrderStatusInvalidException("訂單狀態不允許刪除，目前狀態: " + existingOrderInfo.getStatus());
                }

                // 3. 收集所有商品ID並取得商品資訊
                Set<Integer> productIds = collectProductIdsFromOrderDetails(existingOrderInfo.getOrderDetails());
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(
                                productIds);

                // 4. 計算需要歸還的庫存
                Map<Integer, Integer> stockUpdates = new HashMap<>();
                for (OrderDetail detail : existingOrderInfo.getOrderDetails()) {
                        Integer productId = detail.getProductId();
                        Integer quantityToRestore = detail.getQuantity();
                        GetProductDetailResponse productDetail = productDetailsMap.get(productId);
                        Integer currentStock = productDetail.getStockQty();

                        // 計算新庫存 (newQuantity 為 0)
                        Integer newStock = calculateNewStock(
                                        productId,
                                        currentStock,
                                        quantityToRestore, // oldQuantity is the quantity being restored
                                        0); // newQuantity is 0
                        stockUpdates.put(productId, newStock);

                        // logger.info("訂單取消歸還庫存：商品ID {}，歸還數量 {}", productId, quantityToRestore);
                }

                // 5. 批量更新商品庫存
                batchUpdateProductStock(stockUpdates);

                // 6. 刪除訂單，連鎖刪除訂單明細
                orderInfoRepository.delete(existingOrderInfo);
        }

        /**
         * @param accountId
         */
        // functions to share
        private void validateActiveAccountOrThrow(Integer accountId) {
                GetAccountDetailResponse accountDetail = accountClient.getAccountDetail(accountId);
                if (accountDetail.getStatus().equals("N")) {
                        throw new AccountInactiveException("帳戶狀態停用");
                }
        }

        /**
         * @param orderInfo
         */
        // private void validateOrderStatus(OrderInfo orderInfo) {
        // Integer orderStatus = orderInfo.getStatus();
        // if (orderStatus != 1001) {
        // throw new OrderStatusInvalidException("訂單狀態不允許更新商品項目，目前狀態: " + orderStatus);
        // }
        // }

        /**
         * @param productIds
         * @return Map<Integer, GetProductDetailResponse>
         */
        private Map<Integer, GetProductDetailResponse> batchGetProductDetails(Set<Integer> productIds) {
                if (productIds == null || productIds.isEmpty()) {
                        return Collections.emptyMap();
                }

                Map<Integer, GetProductDetailResponse> productMap = productClient.getProductDetails(productIds);

                // 不應該 Throw Exception，除非歷史訂單也不准看停售商品
                return productMap;
        }

        /**
         * @param stockUpdates
         */
        private void batchUpdateProductStock(Map<Integer, Integer> stockUpdates) {
                if (!stockUpdates.isEmpty()) {
                        productClient.updateProductsStock(stockUpdates);
                        // logger.info("批量更新商品庫存成功");
                }
        }

        /**
         * @param orderDetails
         * @return Set<Integer>
         */
        private Set<Integer> collectProductIdsFromOrderDetails(List<OrderDetail> orderDetails) {
                Set<Integer> productIds = new HashSet<>();
                for (OrderDetail detail : orderDetails) {
                        productIds.add(detail.getProductId());
                }
                return productIds;
        }

        /**
         * 計算單一商品基於舊數量和新數量變更後的新庫存。
         * 這個方法封裝了庫存增減的邏輯和驗證。
         *
         * @param productId        商品 ID
         * @param currentStock     該商品目前的庫存 (從 Product Service 獲取)
         * @param originalQuantity 訂單中該商品的原始數量 (新增時為 0, 刪除時為原數量)
         * @param requestQuantity  訂單中該商品要求的數量 (刪除時為 0)
         * @return 計算後的新庫存數量
         * @throws IllegalArgumentException 如果請求數量為負數，或導致庫存變為負數
         */
        private Integer calculateNewStock(Integer productId, Integer currentStock, Integer originalQuantity,
                        Integer requestQuantity) {
                // 基本驗證
                if (productId == null || currentStock == null || originalQuantity == null || requestQuantity == null) {
                        throw new InvalidRequestException(
                                        "Product ID, current stock, old quantity, and new quantity cannot be null.");
                }
                if (originalQuantity < 0 || requestQuantity < 0) {
                        throw new InvalidRequestException(
                                        "Order quantities cannot be negative for product ID: " + productId);
                }
                if (currentStock < 0) {
                        // 理論上 currentStock 不應為負，但加上just in case
                        // logger.warn("Current stock is negative for product ID: {}. Stock: {}",
                        // productId, currentStock);
                        // 或者根據業務規則拋出例外
                        // throw new IllegalStateException("Current stock cannot be negative for product
                        // ID: " + productId);
                }

                // 計算庫存的淨變化量 (從庫存中"拿出"多少)
                // netChange > 0 表示庫存減少 (拿出更多)
                // netChange < 0 表示庫存增加 (放回一些)
                int netChange = requestQuantity - originalQuantity;

                // 計算變更後的新庫存
                int newQuantity = currentStock - netChange;

                // 驗證：新庫存不能小於 0
                if (newQuantity < 0) {
                        // logger.error("Stock insufficient for product ID: {}. Current: {}, Old Qty:
                        // {}, New Qty: {}, Required Change: {}, Potential New Stock: {}",
                        // productId, currentStock, originalQuantity, requestQuantity, netChange,
                        // newQuantity);
                        // 拋出更詳細的錯誤訊息
                        throw new ProductStockNotEnoughException(String.format(
                                        "商品 %d 庫存不足。目前庫存: %d, 訂單原數量: %d, 訂單新數量: %d。需要額外 %d 個，但庫存不足。",
                                        productId, currentStock, originalQuantity, requestQuantity, netChange));
                }

                // logger.debug("Calculated stock for product ID: {}. Current: {}, Old Qty: {},
                // New Qty: {}, Net Change Required: {}, New Stock: {}",
                // productId, currentStock, originalQuantity, requestQuantity, netChange,
                // newQuantity);

                return newQuantity;
        }

        private Map<Integer, Integer> calculateStockUpdates(
                        Map<Integer, OrderDetail> existingMap,
                        Map<Integer, UpdateOrderDetailRequest> incomingMap) {

                // 取得所有涉及的 Product ID
                Set<Integer> allProductIds = new HashSet<>(existingMap.keySet());
                allProductIds.addAll(incomingMap.keySet());

                // 批次獲取商品資訊 (減少 IO 次數，符合你之前的實務做法)
                Map<Integer, GetProductDetailResponse> productDetails = batchGetProductDetails(
                                allProductIds);

                Map<Integer, Integer> stockUpdates = new HashMap<>();
                for (Integer pid : allProductIds) {
                        int oldQty = existingMap.containsKey(pid) ? existingMap.get(pid).getQuantity() : 0;
                        int newQty = incomingMap.containsKey(pid) ? incomingMap.get(pid).getQuantity() : 0;

                        if (oldQty != newQty) {
                                int currentStock = productDetails.get(pid).getStockQty();
                                // 呼叫你原有的庫存計算邏輯
                                stockUpdates.put(pid, calculateNewStock(pid, currentStock, oldQty, newQty));
                        }
                }
                return stockUpdates;
        }

        /**
         * @param orderInfo
         * @return BigDecimal
         */
        private BigDecimal calculateOrderTotalAmount(OrderInfo orderInfo) {
                List<OrderDetail> orderDetails = orderInfo.getOrderDetails();
                Set<Integer> productIds = collectProductIdsFromOrderDetails(orderDetails);
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(
                                productIds);

                BigDecimal totalAmount = BigDecimal.ZERO;
                for (OrderDetail detail : orderDetails) {
                        GetProductDetailResponse productDetail = productDetailsMap.get(detail.getProductId());
                        totalAmount = totalAmount.add(
                                        productDetail.getPrice().multiply(BigDecimal.valueOf(detail.getQuantity())));
                }
                return totalAmount;
        }

        public OrderInfo findByOrderIdOrThrow(Integer orderId) {
                return orderInfoRepository.findById(orderId).orElseThrow(
                                () -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        }

        /**
         * @param accountId
         * @return boolean
         */
        // 驗證帳戶ID有無關聯的訂單
        public boolean ActiveAccountIdIsInOrder(Integer accountId) {
                if (!orderInfoRepository.findByAccountId(accountId).isEmpty()) {
                        return true;
                }
                return false;
        }

        /**
         * 內部record用於封裝訂單明細處理結果
         */
        private record OrderProcessResult(
                        List<OrderDetail> orderDetails,
                        Map<Integer, Integer> stockUpdates) {
        }

        /**
         * 處理訂單明細：計算庫存變更並建立訂單明細物件
         *
         * @param createOrderRequest 訂單建立請求
         * @param orderInfo          訂單主表
         * @param productDetailsMap  商品詳細資訊Map
         * @return OrderProcessResult 包含訂單明細和庫存更新
         */
        private OrderProcessResult processOrderDetails(
                        CreateOrderRequest createOrderRequest,
                        OrderInfo orderInfo,
                        Map<Integer, GetProductDetailResponse> productDetailsMap) {

                Map<Integer, Integer> stockUpdates = new HashMap<>();

                List<OrderDetail> orderDetails = createOrderRequest.getOrderDetails().stream()
                                .filter(detailRequest -> productDetailsMap.containsKey(detailRequest.getProductId()))
                                .map(detailRequest -> {
                                        Integer productId = detailRequest.getProductId();
                                        Integer quantity = detailRequest.getQuantity();
                                        GetProductDetailResponse productDetail = productDetailsMap.get(productId);

                                        // 計算新庫存
                                        Integer newStock = calculateNewStock(
                                                        productId,
                                                        productDetail.getStockQty(),
                                                        0, // 新增時舊數量為0
                                                        quantity);
                                        stockUpdates.put(productId, newStock);

                                        // 建立訂單明細
                                        return OrderDetail.builder()
                                                        .orderInfo(orderInfo)
                                                        .productId(productId)
                                                        .quantity(quantity)
                                                        .build();
                                })
                                .collect(Collectors.toList());

                return new OrderProcessResult(orderDetails, stockUpdates);
        }
}
