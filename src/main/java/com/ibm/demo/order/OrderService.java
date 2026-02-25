package com.ibm.demo.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ibm.demo.account.AccountClient;
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
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.Entity.OrderDetail;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.util.OrderItemRequest;
import com.ibm.demo.util.ProcessOrderItemsRequest;
import com.ibm.demo.util.ServiceValidator;

import jakarta.transaction.Transactional;

@Service
public class OrderService {
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
                ServiceValidator.validateNotNull(createOrderRequest, "Create order request");
                ServiceValidator.validateNotNull(createOrderRequest.accountId(), "Account ID");
                ServiceValidator.validateNotEmpty(createOrderRequest.orderDetails(), "Order details");
                // 驗證帳戶存在且狀態為啟用
                Integer accountId = createOrderRequest.accountId();
                if (accountClient.getAccountDetail(accountId).status().equals(AccountStatus.INACTIVE.getCode())) {
                        throw new AccountInactiveException("帳戶狀態:" + AccountStatus.INACTIVE.getDescription());
                }

                // 使用 Set 來過濾重複的商品ID，避免同一訂單中同一商品多筆明細造成的庫存計算錯誤
                // 如果有重複商品ID，直接丟出錯誤，要求前端修正訂單明細，因為同一訂單中同一商品多筆明細在業務上通常是不合理的
                Set<OrderItemRequest> uniqueItems = createOrderRequest.orderDetails().stream()
                                .map(detail -> OrderItemRequest.builder()
                                                .productId(detail.productId())
                                                .quantity(detail.quantity())
                                                .build())
                                .collect(Collectors.toSet());
                if (uniqueItems.size() != createOrderRequest.orderDetails().size()) {
                        throw new InvalidRequestException("同一訂單中同一商品只能有一筆明細，請合併重複的商品明細後再提交訂單。");
                }

                ProcessOrderItemsRequest request = ProcessOrderItemsRequest.builder()
                                .originalItems(Collections.emptySet())
                                .updatedItems(uniqueItems)
                                .build();

                productClient.processOrderItems(request);

                // 建立新訂單
                OrderInfo newOrderInfo = OrderInfo.builder()
                                .accountId(accountId)
                                .status(OrderStatus.CREATED.getCode())
                                .build();

                OrderInfo savedOrderInfo = orderInfoRepository.save(newOrderInfo);
                List<OrderDetail> orderDetails = createOrderRequest.orderDetails().stream()
                                .map(detailRequest -> {
                                        Integer productId = detailRequest.productId();
                                        Integer quantity = detailRequest.quantity();
                                        // 建立訂單明細
                                        return OrderDetail.builder()
                                                        .orderInfo(newOrderInfo)
                                                        .productId(productId)
                                                        .quantity(quantity)
                                                        .build();
                                })
                                .collect(Collectors.toList());
                orderDetailRepository.saveAll(orderDetails);
                return savedOrderInfo.getId();
        }

        public List<GetOrderListResponse> getOrderListByAccountId(Integer accountId) {
                ServiceValidator.validateNotNull(accountId, "Account ID");
                List<OrderInfo> orderInfoList = orderInfoRepository.findByAccountId(accountId);
                if (orderInfoList == null || orderInfoList.isEmpty()) {
                        return new ArrayList<>();
                }
                return orderInfoList.stream()
                                .map(orderInfo -> {
                                        GetOrderListResponse response = GetOrderListResponse.builder()
                                                        .orderId(orderInfo.getId())
                                                        .status(orderInfo.getStatus())
                                                        .totalAmount(calculateOrderTotalAmount(orderInfo))
                                                        .build();
                                        return response;
                                }).collect(Collectors.toList());

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
        @Transactional
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

                // 使用 Set 來過濾重複的商品ID，避免同一訂單中同一商品多筆明細造成的庫存計算錯誤
                // 如果有重複商品ID，直接丟出錯誤，要求前端修正訂單明細，因為同一訂單中同一商品多筆明細在業務上通常是不合理的
                Set<OrderItemRequest> uniqueItems = request.items().stream()
                                .map(detail -> OrderItemRequest.builder()
                                                .productId(detail.productId())
                                                .quantity(detail.quantity())
                                                .build())
                                .collect(Collectors.toSet());
                if (uniqueItems.size() != request.items().size()) {
                        throw new InvalidRequestException("同一訂單中同一商品只能有一筆明細，請合併重複的商品明細後再提交訂單。");
                }

                ProcessOrderItemsRequest processRequest = ProcessOrderItemsRequest.builder()
                                .originalItems(originalItems)
                                .updatedItems(uniqueItems)
                                .build();

                productClient.processOrderItems(processRequest);

                // 2. 準備 Map 以便比對
                Map<Integer, OrderDetail> existingMap = order.getOrderDetails().stream()
                                .collect(Collectors.toMap(OrderDetail::getProductId, Function.identity()));
                Map<Integer, UpdateOrderDetailRequest> incomingMap = request.items().stream()
                                .collect(Collectors.toMap(UpdateOrderDetailRequest::productId, Function.identity()));

                // 3. 同步 Entity 集合
                // 3A. 處理更新與刪除
                List<OrderDetail> detailsToRemove = order.getOrderDetails().stream()
                                .filter(detail -> !incomingMap.containsKey(detail.getProductId()))
                                .collect(Collectors.toList());

                order.getOrderDetails().removeAll(detailsToRemove);

                order.getOrderDetails().forEach(detail -> {
                        UpdateOrderDetailRequest incoming = incomingMap.get(detail.getProductId());
                        detail.setQuantity(incoming.quantity());
                });

                // 3B. 處理新增
                request.items().stream()
                                .filter(item -> !existingMap.containsKey(item.productId()))
                                .forEach(item -> order.getOrderDetails().add(
                                                OrderDetail.builder()
                                                                .orderInfo(order)
                                                                .productId(item.productId())
                                                                .quantity(item.quantity())
                                                                .build()));

                // 6. 更新訂單狀態並存檔
                order.setStatus(request.orderStatus());
                orderInfoRepository.save(order);
        }

        /**
         * @param orderId
         */
        @Transactional
        public void deleteOrder(Integer orderId) {
                ServiceValidator.validateNotNull(orderId, "Order ID");
                // 1. 獲取訂單資訊
                OrderInfo existingOrderInfo = orderInfoRepository.findById(orderId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Order not found with ID: " + orderId));

                // 2. 驗證訂單狀態
                if (existingOrderInfo.getStatus() != OrderStatus.CREATED.getCode()) {
                        throw new OrderStatusInvalidException("訂單狀態不允許刪除，目前狀態: " + existingOrderInfo.getStatus());
                }

                // 3. 收集所有商品ID並取得商品資訊
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

                // 6. 刪除訂單，連鎖刪除訂單明細
                orderInfoRepository.delete(existingOrderInfo);
        }

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

        public OrderInfo findOrderByIdOrThrow(Integer orderId) {
                ServiceValidator.validateNotNull(orderId, "Order ID");
                return orderInfoRepository.findById(orderId).orElseThrow(
                                () -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        }

        /**
         * @param accountId
         * @return boolean
         */
        // 驗證帳戶ID有無關聯的訂單
        public boolean ActiveAccountIdIsInOrder(Integer accountId) {
                ServiceValidator.validateNotNull(accountId, "Account ID");
                if (!orderInfoRepository.findByAccountId(accountId).isEmpty()) {
                        return true;
                }
                return false;
        }

}
