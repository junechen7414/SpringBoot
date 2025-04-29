package com.ibm.demo.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ibm.demo.account.AccountClient;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
import com.ibm.demo.order.DTO.CreateOrderDetailResponse;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.CreateOrderResponse;
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.DTO.OrderItemDTO;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderDetailResponse;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.DTO.UpdateOrderResponse;
import com.ibm.demo.order.Entity.OrderDetail;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.ProductRepository;
import com.ibm.demo.product.DTO.GetProductDetailResponse;

import jakarta.transaction.Transactional;

@Service
public class OrderService {
        private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
        private OrderInfoRepository orderInfoRepository;
        private OrderDetailRepository orderDetailRepository;
        private final AccountClient accountClient;
        private final ProductClient productClient;

        public OrderService(OrderInfoRepository orderInfoRepository, AccountRepository accountRepository,
                        OrderDetailRepository orderDetailRepository,
                        ProductRepository productRepository, AccountClient accountClient, ProductClient productClient) {
                this.orderInfoRepository = orderInfoRepository;
                this.orderDetailRepository = orderDetailRepository;
                this.accountClient = accountClient;
                this.productClient = productClient;
        }

        @Transactional
        public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
                Integer accountId = createOrderRequest.getAccountId();
                validateActiveAccount(accountId);

                OrderInfo newOrderInfo = new OrderInfo();
                newOrderInfo.setAccountId(accountId);
                newOrderInfo.setStatus(1001);
                OrderInfo savedOrderInfo = orderInfoRepository.save(newOrderInfo);

                // 收集商品ID
                Set<Integer> productIds = new HashSet<>();
                for (CreateOrderDetailRequest detailRequest : createOrderRequest.getOrderDetails()) {
                        productIds.add(detailRequest.getProductId());
                }

                // 使用共用方法獲取商品資訊
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(productIds);

                Map<Integer, Integer> stockUpdates = new HashMap<>();
                List<OrderDetail> orderDetailsToBeCreated = new ArrayList<>();
                List<CreateOrderDetailResponse> orderDetailResponses = new ArrayList<>();
                BigDecimal totalAmount = BigDecimal.ZERO;

                for (CreateOrderDetailRequest detailRequest : createOrderRequest.getOrderDetails()) {
                        Integer requestProductId = detailRequest.getProductId();
                        Integer requestQuantity = detailRequest.getQuantity();
                        GetProductDetailResponse productDetail = productDetailsMap.get(requestProductId);

                        // 使用共用方法計算庫存變更
                        Map<Integer, Integer> itemStockUpdate = calculateStockChanges(
                                        requestProductId,
                                        requestQuantity,
                                        productDetail,
                                        true);
                        stockUpdates.putAll(itemStockUpdate);

                        // 建立訂單明細
                        OrderDetail newOrderDetail = new OrderDetail(savedOrderInfo, requestProductId,
                                        requestQuantity);
                        orderDetailsToBeCreated.add(newOrderDetail);

                        // 準備回應
                        CreateOrderDetailResponse detailResponse = new CreateOrderDetailResponse(
                                        requestProductId, requestQuantity, productDetail.getPrice());
                        orderDetailResponses.add(detailResponse);

                        // 計算總金額
                        BigDecimal subTotalAmount = productDetail.getPrice()
                                        .multiply(BigDecimal.valueOf(requestQuantity));
                        totalAmount = totalAmount.add(subTotalAmount);
                }

                // 使用共用方法更新庫存
                batchUpdateProductStock(stockUpdates);

                // 批量建立訂單明細
                orderDetailRepository.saveAll(orderDetailsToBeCreated);

                return new CreateOrderResponse(savedOrderInfo.getId(), accountId,
                                savedOrderInfo.getStatus(), totalAmount,
                                savedOrderInfo.getCreateDate(), orderDetailResponses);
        }

        public List<GetOrderListResponse> getOrderList(Integer accountId) {
                validateActiveAccount(accountId);
                logger.info("找到啟用中帳戶，ID: {}", accountId);

                List<OrderInfo> orderInfoList = orderInfoRepository.findByAccountId(accountId);
                List<GetOrderListResponse> getOrderListResponse = new ArrayList<>();

                for (OrderInfo orderInfo : orderInfoList) {
                        BigDecimal totalAmount = calculateOrderTotalAmount(orderInfo); // 使用新方法計算總金額
                        GetOrderListResponse response = new GetOrderListResponse();
                        response.setOrderId(orderInfo.getId());
                        response.setStatus(orderInfo.getStatus());
                        response.setTotalAmount(totalAmount);
                        getOrderListResponse.add(response);
                }

                return getOrderListResponse;
        }

        public GetOrderDetailResponse getOrderDetails(Integer orderId) {
                // 1. 獲取訂單基本資訊
                OrderInfo existingOrderInfo = orderInfoRepository.findById(orderId)
                                .orElseThrow(() -> new NullPointerException("Order not found with id: " + orderId));

                // 2. 獲取訂單明細
                List<OrderDetail> orderDetails = existingOrderInfo.getOrderDetails();

                // 3. 收集所有商品ID
                Set<Integer> productIds = new HashSet<>();
                for (OrderDetail detail : orderDetails) {
                        productIds.add(detail.getProductId());
                }

                // 4. 批量獲取商品資訊
                Map<Integer, GetProductDetailResponse> productDetailsMap = productClient.getProductDetails(productIds);

                // 5. 建立回應DTO
                GetOrderDetailResponse response = new GetOrderDetailResponse();
                response.setAccountId(existingOrderInfo.getAccountId());
                response.setOrderStatus(existingOrderInfo.getStatus());
                response.setTotalAmount(calculateOrderTotalAmount(existingOrderInfo));

                // 6. 建立訂單項目列表
                List<OrderItemDTO> itemDTOs = new ArrayList<>();
                for (OrderDetail orderDetail : orderDetails) {
                        OrderItemDTO itemDTO = new OrderItemDTO();
                        Integer productId = orderDetail.getProductId();
                        GetProductDetailResponse productDetail = productDetailsMap.get(productId);

                        itemDTO.setProductId(productId);
                        itemDTO.setProductName(productDetail.getName());
                        itemDTO.setQuantity(orderDetail.getQuantity());
                        itemDTO.setProductPrice(productDetail.getPrice());
                        itemDTOs.add(itemDTO);
                }

                response.setItems(itemDTOs);
                return response;
        }

        @Transactional
        public UpdateOrderResponse updateOrder(UpdateOrderRequest updateOrderRequest) {
                // 1. 驗證訂單存在和狀態
                Integer orderId = updateOrderRequest.getOrderId();
                OrderInfo existingOrderInfo = orderInfoRepository.findById(orderId)
                                .orElseThrow(() -> new NullPointerException("Order not found with id: " + orderId));

                validateOrderStatus(existingOrderInfo);
                logger.info("找到要更新的訂單，ID: {}", orderId);

                // 2. 建立現有明細的 Map
                Map<Integer, OrderDetail> existingDetailsMap = new HashMap<>();
                for (OrderDetail detail : existingOrderInfo.getOrderDetails()) {
                        existingDetailsMap.put(detail.getProductId(), detail);
                }
                // 3. 建立更新明細的 Map
                Map<Integer, UpdateOrderDetailRequest> updatedItemsMap = new HashMap<>();
                for (UpdateOrderDetailRequest detailRequest : updateOrderRequest.getItems()) {
                        updatedItemsMap.put(detailRequest.getProductId(), detailRequest);
                }

                // 現有明細和更新後的明細比對會有四種情況:
                // 兩種情況是其中一邊有獨立的productId，分別代表更新後的明細中要新增product或是要移除product；
                // 兩種情況是兩邊有共同的productId，則看quantity是沒變還是有變，分別代表沒更改和更改數量。

                // keySet即是把Map中的key作為Set，這邊拿來分上面四種情況
                Set<Integer> existingProductIds = existingDetailsMap.keySet();
                Set<Integer> updatedProductIds = updatedItemsMap.keySet();

                // 取出更新後新增的產品id
                Set<Integer> newProductIds = new HashSet<>(updatedProductIds);
                newProductIds.removeAll(existingProductIds);

                // 取出更新後刪除的產品id
                Set<Integer> removedProductIds = new HashSet<>(existingProductIds);
                removedProductIds.removeAll(updatedProductIds);

                // 取出共同存在的產品 ID (可能已更新)
                Set<Integer> commonProductIds = new HashSet<>(existingProductIds);
                commonProductIds.retainAll(updatedProductIds);

                // 5. 收集所有需要查詢的商品 ID
                Set<Integer> productIdsToQuery = new HashSet<>();
                productIdsToQuery.addAll(newProductIds);
                productIdsToQuery.addAll(commonProductIds);

                // 6. 批量獲取商品資訊
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(productIdsToQuery);

                // 7. 準備庫存更新資訊
                Map<Integer, Integer> stockUpdates = new HashMap<>();

                // 8. 處理新增項目
                List<OrderDetail> orderDetailsToAdd = new ArrayList<>();
                for (Integer productId : newProductIds) {
                        UpdateOrderDetailRequest newItem = updatedItemsMap.get(productId);
                        GetProductDetailResponse productDetail = productDetailsMap.get(productId);

                        // 計算庫存變更
                        Map<Integer, Integer> itemStockUpdate = calculateStockChanges(
                                        productId,
                                        newItem.getQuantity(),
                                        productDetail,
                                        true);

                        stockUpdates.putAll(itemStockUpdate);
                }

                // 9. 處理移除項目
                List<OrderDetail> orderDetailsToRemove = new ArrayList<>();
                for (Integer productId : removedProductIds) {
                        OrderDetail detailToRemove = existingDetailsMap.get(productId);

                        // 獲取原始數量，用於歸還庫存
                        Integer quantityToRestore = detailToRemove.getQuantity();
                        GetProductDetailResponse productDetail = productDetailsMap.get(productId);

                        // 計算歸還後的庫存
                        Integer currentStock = productDetail.getStockQty();
                        Integer newStock = currentStock + quantityToRestore;

                        // 更新庫存變更集合
                        stockUpdates.put(productId, newStock);

                        // 加入待刪除列表
                        orderDetailsToRemove.add(detailToRemove);

                        logger.info("訂單 {} 移除商品項目：產品ID {}, 數量 {}",
                                        orderId, productId, quantityToRestore);
                }

                // 10. 處理更新項目
                List<OrderDetail> orderDetailsToUpdate = new ArrayList<>();
                for (Integer productId : commonProductIds) {
                        OrderDetail existingDetail = existingDetailsMap.get(productId);
                        UpdateOrderDetailRequest updatedRequest = updatedItemsMap.get(productId);
                        Integer oldQuantity = existingDetail.getQuantity();
                        Integer newQuantity = updatedRequest.getQuantity();

                        // 只處理數量有變動的項目
                        if (!oldQuantity.equals(newQuantity)) {
                                GetProductDetailResponse productDetail = productDetailsMap.get(productId);
                                Integer currentStock = productDetail.getStockQty();
                                Integer quantityDifference = newQuantity - oldQuantity;

                                // 檢查庫存是否足夠
                                if (currentStock < quantityDifference) {
                                        throw new IllegalArgumentException("商品" + productId + "庫存不足");
                                }

                                // 計算新庫存
                                Integer newStock = currentStock - quantityDifference;
                                stockUpdates.put(productId, newStock);

                                // 更新訂單明細
                                existingDetail.setQuantity(newQuantity);
                                orderDetailsToUpdate.add(existingDetail);

                                logger.info("訂單 {} 商品項目產品ID {} 數量由 {} 更新為 {}",
                                                orderId, productId, oldQuantity, newQuantity);
                        }
                }

                // 11. 執行批量更新
                if (!stockUpdates.isEmpty()) {
                        productClient.updateProductsStock(stockUpdates);
                        logger.info("批量更新商品庫存成功");
                }

                if (!orderDetailsToAdd.isEmpty()) {
                        orderDetailRepository.saveAll(orderDetailsToAdd);
                        logger.info("批量新增 {} 個訂單明細成功", orderDetailsToAdd.size());
                }

                if (!orderDetailsToUpdate.isEmpty()) {
                        orderDetailRepository.saveAll(orderDetailsToUpdate);
                        logger.info("批量更新 {} 個訂單明細成功", orderDetailsToUpdate.size());
                }

                if (!orderDetailsToRemove.isEmpty()) {
                        orderDetailRepository.deleteAll(orderDetailsToRemove);
                        logger.info("批量刪除 {} 個訂單明細成功", orderDetailsToRemove.size());
                }

                // 12. 重新計算訂單總金額並準備回應
                BigDecimal totalAmount = BigDecimal.ZERO;
                List<UpdateOrderDetailResponse> itemsResponse = new ArrayList<>();

                // 取得最新的訂單明細
                List<OrderDetail> updatedDetails = existingOrderInfo.getOrderDetails();

                // 收集所有商品ID用於獲取最新的商品資訊
                Set<Integer> allProductIds = new HashSet<>();
                for (OrderDetail detail : updatedDetails) {
                        allProductIds.add(detail.getProductId());
                }

                // 計算總金額並準備回應項目
                for (OrderDetail detail : updatedDetails) {
                        Integer productId = detail.getProductId();
                        Integer quantity = detail.getQuantity();
                        GetProductDetailResponse productDetail = productDetailsMap.get(productId);
                        BigDecimal price = productDetail.getPrice(); // 改用商品服務的即時價格

                        // 計算總金額
                        totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(quantity)));

                        // 準備回應項目
                        UpdateOrderDetailResponse itemResponse = new UpdateOrderDetailResponse();
                        itemResponse.setProductId(productId);
                        itemResponse.setQuantity(quantity);
                        itemsResponse.add(itemResponse);

                        logger.info("更新後的訂單項目：產品ID {}，數量 {}", productId, quantity);
                }

                // 設置回應
                UpdateOrderResponse response = new UpdateOrderResponse();
                response.setOrderId(existingOrderInfo.getId());
                response.setTotalAmount(totalAmount);
                response.setItems(itemsResponse);

                logger.info("訂單更新完成，ID: {}，總金額: {}", orderId, totalAmount);

                return response;
        }

        @Transactional
        public void deleteOrder(Integer orderId) {
                // 1. 獲取訂單資訊
                OrderInfo existingOrderInfo = orderInfoRepository.findById(orderId)
                                .orElseThrow(() -> new NullPointerException("Order not found with id: " + orderId));

                logger.info("找到要刪除的訂單，ID: {}", orderId);

                // 2. 驗證訂單狀態
                validateOrderStatus(existingOrderInfo);

                // 3. 收集所有商品ID並取得商品資訊
                Set<Integer> productIds = collectProductIdsFromOrderDetails(existingOrderInfo.getOrderDetails());
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(productIds);

                // 4. 計算需要歸還的庫存
                Map<Integer, Integer> stockUpdates = new HashMap<>();
                for (OrderDetail detail : existingOrderInfo.getOrderDetails()) {
                        Integer productId = detail.getProductId();
                        Integer quantityToRestore = detail.getQuantity();
                        GetProductDetailResponse productDetail = productDetailsMap.get(productId);

                        Integer currentStock = productDetail.getStockQty();
                        Integer newStock = currentStock + quantityToRestore;
                        stockUpdates.put(productId, newStock);

                        logger.info("訂單取消歸還庫存：商品ID {}，歸還數量 {}", productId, quantityToRestore);
                }

                // 5. 批量更新商品庫存
                batchUpdateProductStock(stockUpdates);

                // 6. 更新訂單狀態為已刪除(1003)
                existingOrderInfo.setStatus(1003);
                orderInfoRepository.save(existingOrderInfo);

                logger.info("訂單已刪除，ID: {}", orderId);
        }

        // functions to share
        private void validateActiveAccount(Integer accountId) {
                accountClient.validateActiveAccount(accountId);
                logger.info("找到啟用中帳戶，ID: {}", accountId);
        }

        private void validateOrderStatus(OrderInfo orderInfo) {
                Integer orderStatus = orderInfo.getStatus();
                if (orderStatus != 1001) {
                        throw new IllegalArgumentException("訂單狀態不允許更新商品項目，目前狀態: " + orderStatus);
                }
        }

        private Map<Integer, GetProductDetailResponse> batchGetProductDetails(Set<Integer> productIds) {
                if (productIds.isEmpty()) {
                        return new HashMap<>();
                }
                Map<Integer, GetProductDetailResponse> productDetailsMap = productClient.getProductDetails(productIds);
                logger.info("從 Product Service 獲取 {} 個商品的詳細資訊", productDetailsMap.size());
                return productDetailsMap;
        }

        private void batchUpdateProductStock(Map<Integer, Integer> stockUpdates) {
                if (!stockUpdates.isEmpty()) {
                        productClient.updateProductsStock(stockUpdates);
                        logger.info("批量更新商品庫存成功");
                }
        }

        private Set<Integer> collectProductIdsFromOrderDetails(List<OrderDetail> orderDetails) {
                Set<Integer> productIds = new HashSet<>();
                for (OrderDetail detail : orderDetails) {
                        productIds.add(detail.getProductId());
                }
                return productIds;
        }

        private Map<Integer, Integer> calculateStockChanges(
                        Integer productId,
                        Integer requestQuantity,
                        GetProductDetailResponse productDetail,
                        boolean isDecrease) {

                Map<Integer, Integer> stockUpdates = new HashMap<>();
                Integer currentStock = productDetail.getStockQty();
                Integer newStock;

                if (isDecrease) {
                        newStock = currentStock - requestQuantity;
                        if (newStock < 0) {
                                throw new IllegalArgumentException("商品" + productId + "庫存不足");
                        }
                } else {
                        newStock = currentStock + requestQuantity;
                }

                stockUpdates.put(productId, newStock);
                return stockUpdates;
        }

        private BigDecimal calculateOrderTotalAmount(OrderInfo orderInfo) {
                List<OrderDetail> orderDetails = orderInfo.getOrderDetails();
                Set<Integer> productIds = collectProductIdsFromOrderDetails(orderDetails);
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(productIds);

                BigDecimal totalAmount = BigDecimal.ZERO;
                for (OrderDetail detail : orderDetails) {
                        GetProductDetailResponse productDetail = productDetailsMap.get(detail.getProductId());
                        totalAmount = totalAmount.add(
                                        productDetail.getPrice().multiply(BigDecimal.valueOf(detail.getQuantity())));
                }
                return totalAmount;
        }
}