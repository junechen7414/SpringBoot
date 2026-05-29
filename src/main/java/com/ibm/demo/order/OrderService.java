package com.ibm.demo.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ibm.demo.account.AccountClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.InvalidRequestException;
import com.ibm.demo.exception.BusinessLogicCheck.OrderStatusInvalidException;
import com.ibm.demo.exception.BusinessLogicCheck.ResourceNotFoundException;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.DTO.OrderItemDTO;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.Entity.OrderDetail;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.util.OrderItemRequest;
import com.ibm.demo.util.ProcessOrderItemsRequest;
import com.ibm.demo.util.ServiceValidator;

import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
@Bulkhead(name = "OrderService")
@CircuitBreaker(name = "OrderService")
@RateLimiter(name = "OrderService")
public class OrderService {
        private final OrderInfoRepository orderInfoRepository;
        private final AccountClient accountClient;
        private final ProductClient productClient;
        private final OrderTransactionalService orderTransactionalService;

        /**
         * 注入Repository和Client，已用lombok註解RequiredArgsConstructor定義建構子。
         * 
         * @param orderInfoRepository   訂單主檔資料庫存取介面
         * @param orderDetailRepository 訂單明細資料庫存取介面
         * @param accountClient         帳戶服務的Client，用於驗證帳戶狀態
         * @param productClient         商品服務的Client，用於驗證商品庫存和獲取商品資訊
         */

        /**
         * @param createOrderRequest
         */
        public Integer createOrder(CreateOrderRequest createOrderRequest) {
                ServiceValidator.validateNotNull(createOrderRequest, "Create order request");
                ServiceValidator.validateNotNull(createOrderRequest.accountId(), "Account ID");
                ServiceValidator.validateNotEmpty(createOrderRequest.orderDetails(), "Order details");
                // 驗證帳戶存在且狀態為啟用
                Integer accountId = createOrderRequest.accountId();
                if (accountClient.getAccountDetail(accountId).status().equals(AccountStatus.INACTIVE.getCode())) {
                        throw new AccountInactiveException("帳戶狀態:" + AccountStatus.INACTIVE.getDescription());
                }

                // 驗證並轉換訂單明細，確保同一訂單中同一商品只有一筆明細
                Set<OrderItemRequest> uniqueItems = validateAndConvertToUniqueItems(
                                createOrderRequest.orderDetails(),
                                detail -> detail.productId(),
                                detail -> detail.quantity());

                ProcessOrderItemsRequest request = ProcessOrderItemsRequest.builder()
                                .originalItems(Collections.emptySet())
                                .updatedItems(uniqueItems)
                                .build();

                productClient.processOrderItems(request);

                // 將資料庫操作委派給交易服務，若失敗則補償歸還庫存
                try {
                        return orderTransactionalService.createOrder(createOrderRequest);
                } catch (Exception e) {
                        // 補償: 歸還已扣除的庫存 (反轉 original 和 updated)
                        ProcessOrderItemsRequest rollbackRequest = ProcessOrderItemsRequest.builder()
                                        .originalItems(uniqueItems)
                                        .updatedItems(Collections.emptySet())
                                        .build();
                        try {
                                productClient.processOrderItems(rollbackRequest);
                        } catch (Exception compensationEx) {
                                log.error("建立訂單失敗後，補償歸還庫存也失敗，需人工介入處理。" +
                                                "帳戶ID: {}, 商品清單: {}, 原始異常: {}, 補償異常: {}",
                                                createOrderRequest.accountId(),
                                                uniqueItems.stream()
                                                                .map(item -> String.format("商品%d(數量%d)", item.productId(), item.quantity()))
                                                                .collect(Collectors.joining(", ")),
                                                e.getMessage(),
                                                compensationEx.getMessage(),
                                                compensationEx);
                        }
                        throw e;
                }
        }

        public List<GetOrderListResponse> getOrderListByAccountId(Integer accountId) {
                ServiceValidator.validateNotNull(accountId, "Account ID");
                List<OrderInfo> orderInfoList = orderInfoRepository.findByAccountId(accountId);
                if (orderInfoList == null || orderInfoList.isEmpty()) {
                        return new ArrayList<>();
                }
                
                // 收集所有訂單涉及的商品 ID
                Set<Integer> allProductIds = orderInfoList.stream()
                                .flatMap(order -> order.getOrderDetails().stream())
                                .map(OrderDetail::getProductId)
                                .collect(Collectors.toSet());
                
                // 一次性批量查詢所有商品
                Map<Integer, GetProductDetailResponse> productMap = batchGetProductDetails(allProductIds);
                
                // 計算每個訂單的總金額
                return orderInfoList.stream()
                                .map(orderInfo -> GetOrderListResponse.builder()
                                                .orderId(orderInfo.getId())
                                                .status(orderInfo.getStatus())
                                                .totalAmount(calculateOrderTotalAmount(orderInfo, productMap))
                                                .build())
                                .collect(Collectors.toList());
        }

        /**
         * @param orderId
         * @return GetOrderDetailResponse
         */
        public GetOrderDetailResponse getOrderDetailByOrderId(Integer orderId) {
                // 1. 獲取訂單主檔（找不到直接噴 404）
                OrderInfo orderInfo = findOrderByIdOrThrow(orderId);
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
                                        return OrderItemDTO.builder()
                                                        .productId(detail.getProductId())
                                                        .productName(product.name())
                                                        .quantity(detail.getQuantity())
                                                        .productPrice(product.price())
                                                        .build();
                                })
                                .collect(Collectors.toList());

                // 4. 回傳結果
                GetOrderDetailResponse response = GetOrderDetailResponse.builder()
                                .accountId(orderInfo.getAccountId())
                                .orderStatus(orderInfo.getStatus())
                                .totalAmount(calculateOrderTotalAmount(orderInfo))
                                .items(itemDTOs)
                                .build();
                return response;
        }

        /**
         * @param updateOrderRequest
         * @return UpdateOrderResponse
         */
        public void updateOrder(UpdateOrderRequest request) {
                ServiceValidator.validateNotNull(request, "Update order request");
                ServiceValidator.validateNotNull(request.orderId(), "Update order id");
                ServiceValidator.validateNotEmpty(request.orderStatus(), "Update order status");
                ServiceValidator.validateNotEmpty(request.items(), "Update order items");
                // 1. 獲取現有訂單
                OrderInfo order = findOrderByIdOrThrow(request.orderId());
                Set<OrderItemRequest> originalItems = order.getOrderDetails().stream()
                                .map(detail -> OrderItemRequest.builder()
                                                .productId(detail.getProductId())
                                                .quantity(detail.getQuantity())
                                                .build())
                                .collect(Collectors.toSet());

                // 驗證並轉換訂單明細，確保同一訂單中同一商品只有一筆明細
                Set<OrderItemRequest> uniqueItems = validateAndConvertToUniqueItems(
                                request.items(),
                                detail -> detail.productId(),
                                detail -> detail.quantity());

                ProcessOrderItemsRequest processRequest = ProcessOrderItemsRequest.builder()
                                .originalItems(originalItems)
                                .updatedItems(uniqueItems)
                                .build();

                productClient.processOrderItems(processRequest);

                // 將資料庫操作委派給交易服務，若失敗則補償反轉庫存操作
                try {
                        orderTransactionalService.updateOrder(request, order);
                } catch (Exception e) {
                        // 補償: 反轉庫存操作 (將 original 和 updated 互換)
                        ProcessOrderItemsRequest rollbackRequest = ProcessOrderItemsRequest.builder()
                                        .originalItems(uniqueItems)
                                        .updatedItems(originalItems)
                                        .build();
                        try {
                                productClient.processOrderItems(rollbackRequest);
                        } catch (Exception compensationEx) {
                                log.error("更新訂單失敗後，補償反轉庫存也失敗，需人工介入處理。" +
                                                "訂單ID: {}, 帳戶ID: {}, 原商品清單: {}, 新商品清單: {}, 原始異常: {}, 補償異常: {}",
                                                request.orderId(),
                                                order.getAccountId(),
                                                originalItems.stream()
                                                                .map(item -> String.format("商品%d(數量%d)", item.productId(), item.quantity()))
                                                                .collect(Collectors.joining(", ")),
                                                uniqueItems.stream()
                                                                .map(item -> String.format("商品%d(數量%d)", item.productId(), item.quantity()))
                                                                .collect(Collectors.joining(", ")),
                                                e.getMessage(),
                                                compensationEx.getMessage(),
                                                compensationEx);
                        }
                        throw e;
                }
        }

        /**
         * @param orderId
         */
        public void deleteOrder(Integer orderId) {
                ServiceValidator.validateNotNull(orderId, "Order ID");
                // 1. 獲取訂單資訊
                OrderInfo existingOrderInfo = findOrderByIdOrThrow(orderId);

                // 2. 驗證訂單狀態
                if (existingOrderInfo.getStatus() != OrderStatus.CREATED.getCode()) {
                        throw new OrderStatusInvalidException("訂單狀態不允許刪除，目前狀態: " + existingOrderInfo.getStatus());
                }

                // 3. 先歸還庫存（外部服務調用放在前面，失敗時訂單不受影響）
                Set<OrderItemRequest> originalItems = existingOrderInfo.getOrderDetails().stream()
                                .map(detail -> OrderItemRequest.builder()
                                                .productId(detail.getProductId())
                                                .quantity(detail.getQuantity())
                                                .build())
                                .collect(Collectors.toSet());

                ProcessOrderItemsRequest processRequest = ProcessOrderItemsRequest.builder()
                                .originalItems(originalItems)
                                .updatedItems(Collections.emptySet())
                                .build();

                productClient.processOrderItems(processRequest);

                // 4. 再刪除訂單，若失敗則補償重新扣回庫存
                try {
                        orderTransactionalService.deleteOrder(existingOrderInfo, existingOrderInfo.getVersion());
                } catch (Exception e) {
                        // 補償: 重新扣回已歸還的庫存
                        ProcessOrderItemsRequest rollbackRequest = ProcessOrderItemsRequest.builder()
                                        .originalItems(Collections.emptySet())
                                        .updatedItems(originalItems)
                                        .build();
                        try {
                                productClient.processOrderItems(rollbackRequest);
                        } catch (Exception compensationEx) {
                                log.error("刪除訂單失敗後，補償重新扣回庫存也失敗，需人工介入處理。" +
                                                "訂單ID: {}, 帳戶ID: {}, 商品清單: {}, 原始異常: {}, 補償異常: {}",
                                                orderId,
                                                existingOrderInfo.getAccountId(),
                                                originalItems.stream()
                                                                .map(item -> String.format("商品%d(數量%d)", item.productId(), item.quantity()))
                                                                .collect(Collectors.joining(", ")),
                                                e.getMessage(),
                                                compensationEx.getMessage(),
                                                compensationEx);
                        }
                        throw e;
                }
        }

        /**
         * @param productIds
         * @return Map<Integer, GetProductDetailResponse>
         */
        private Map<Integer, GetProductDetailResponse> batchGetProductDetails(Set<Integer> productIds) {
                if (productIds == null || productIds.isEmpty()) {
                        return Collections.emptyMap();
                }

                List<GetProductDetailResponse> productList = productClient.getProductDetails(productIds);

                // 將 List 轉換為 Map，方便後續根據 ID 查找
                Map<Integer, GetProductDetailResponse> productMap = productList.stream()
                                .collect(Collectors.toMap(
                                                GetProductDetailResponse::id,
                                                product -> product));

                // 不應該 Throw Exception，除非歷史訂單也不准看停售商品
                return productMap;
        }

        /**
         * @param orderInfo
         * @return BigDecimal
         */
        private BigDecimal calculateOrderTotalAmount(OrderInfo orderInfo) {
                List<OrderDetail> orderDetails = orderInfo.getOrderDetails();
                Set<Integer> productIds = orderInfo.getOrderDetails().stream()
                                .map(OrderDetail::getProductId)
                                .collect(Collectors.toSet());
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(
                                productIds);

                BigDecimal totalAmount = BigDecimal.ZERO;
                for (OrderDetail detail : orderDetails) {
                        GetProductDetailResponse productDetail = productDetailsMap.get(detail.getProductId());
                        totalAmount = totalAmount.add(
                                        productDetail.price().multiply(BigDecimal.valueOf(detail.getQuantity())));
                }
                return totalAmount;
        }

        /**
         * 
         * @param orderInfo 訂單資訊
         * @param productMap 已批量查詢的商品資訊 Map
         * @return 訂單總金額
         */
        private BigDecimal calculateOrderTotalAmount(OrderInfo orderInfo, 
                                                     Map<Integer, GetProductDetailResponse> productMap) {
                return orderInfo.getOrderDetails().stream()
                                .map(detail -> {
                                        GetProductDetailResponse product = productMap.get(detail.getProductId());
                                        return product.price().multiply(BigDecimal.valueOf(detail.getQuantity()));
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public OrderInfo findOrderByIdOrThrow(Integer orderId) {
                ServiceValidator.validateNotNull(orderId, "Order ID");
                return orderInfoRepository.findById(orderId).orElseThrow(
                                () -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        }

        /**
         * 驗證並轉換訂單明細為唯一商品集合
         * 確保同一訂單中同一商品只有一筆明細
         * 
         * @param items 訂單明細列表
         * @return 唯一的訂單商品集合
         * @throws InvalidRequestException 當存在重複商品時
         */
        private <T> Set<OrderItemRequest> validateAndConvertToUniqueItems(List<T> items, 
                                                                           java.util.function.Function<T, Integer> productIdExtractor,
                                                                           java.util.function.Function<T, Integer> quantityExtractor) {
                Set<OrderItemRequest> uniqueItems = items.stream()
                                .map(item -> OrderItemRequest.builder()
                                                .productId(productIdExtractor.apply(item))
                                                .quantity(quantityExtractor.apply(item))
                                                .build())
                                .collect(Collectors.toSet());
                
                if (uniqueItems.size() != items.size()) {
                        throw new InvalidRequestException("同一訂單中同一商品只能有一筆明細，請合併重複的商品明細後再提交訂單。");
                }
                return uniqueItems;
        }

        /**
         * 驗證帳戶是否有關聯的訂單
         * 
         * @param accountId 帳戶ID
         * @return 若帳戶有關聯訂單則返回 true，否則返回 false
         */
        public boolean isActiveAccountInOrder(Integer accountId) {
                ServiceValidator.validateNotNull(accountId, "Account ID");
                return !orderInfoRepository.findByAccountId(accountId).isEmpty();
        }

}
