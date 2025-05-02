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
import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.NotFound.OrderNotFoundException;
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
import com.ibm.demo.product.DTO.GetProductDetailResponse;

import jakarta.transaction.Transactional;

@Service
public class OrderService {
        private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
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

        @Transactional
        public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
                // 驗證帳戶存在且狀態非不啟用
                Integer accountId = createOrderRequest.getAccountId();
                validateActiveAccount(accountId);
                logger.info("找到啟用中帳戶，ID: {}", accountId);

                // 宣告新訂單並初始化
                OrderInfo newOrderInfo = new OrderInfo();
                newOrderInfo.setAccountId(accountId);
                newOrderInfo.setStatus(1001);

                // 儲存訂單
                OrderInfo savedOrderInfo = orderInfoRepository.save(newOrderInfo);

                // 收集Request所有商品ID
                Set<Integer> productIds = new HashSet<>();
                for (CreateOrderDetailRequest detailRequest : createOrderRequest.getOrderDetails()) {
                        productIds.add(detailRequest.getProductId());
                }

                // 使用收集到的商品ID呼叫Product的端點獲取商品資訊，該端點驗證傳入是否null或空集合，
                // 若不是再使用該ID集合查詢，若有找不到的ID即拋出例外，若全找到的話驗證狀態是否皆為可銷售
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(productIds);

                // 宣告並初始化要用到的變數
                Map<Integer, Integer> stockUpdates = new HashMap<>();
                List<OrderDetail> orderDetailsToBeCreated = new ArrayList<>();
                List<CreateOrderDetailResponse> orderDetailResponses = new ArrayList<>();
                BigDecimal totalAmount = BigDecimal.ZERO;

                // 遍歷訂單明細
                for (CreateOrderDetailRequest detailRequest : createOrderRequest.getOrderDetails()) {
                        Integer requestProductId = detailRequest.getProductId();
                        Integer requestQuantity = detailRequest.getQuantity();
                        GetProductDetailResponse productDetail = productDetailsMap.get(requestProductId);

                        // 使用共用方法計算庫存變更
                        Integer newStock = calculateNewStock(
                                        requestProductId,
                                        productDetail.getStockQty(),
                                        0, // oldQuantity for new item is 0
                                        requestQuantity); // newQuantity is the requested quantity
                        stockUpdates.put(requestProductId, newStock);

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
                        BigDecimal totalAmount = calculateOrderTotalAmount(orderInfo); // 使用共用方法計算總金額
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
                                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

                // 2. 獲取訂單明細
                List<OrderDetail> orderDetails = existingOrderInfo.getOrderDetails();

                // 3. 收集所有商品ID
                Set<Integer> productIds = new HashSet<>();
                for (OrderDetail detail : orderDetails) {
                        productIds.add(detail.getProductId());
                }

                // 4. 批量獲取商品資訊
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(productIds);

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
                                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

                if (existingOrderInfo.getStatus() != 1001) {
                        throw new InvalidRequestException("訂單狀態不允許更新商品項目，目前狀態: " + existingOrderInfo.getStatus());
                }
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
                productIdsToQuery.addAll(removedProductIds);

                // 6. 批量獲取商品資訊
                Map<Integer, GetProductDetailResponse> productDetailsMap = batchGetProductDetails(productIdsToQuery);

                // 7. 準備庫存更新資訊
                Map<Integer, Integer> stockUpdates = new HashMap<>();

                // 8. 處理新增項目
                List<OrderDetail> orderDetailsToAdd = new ArrayList<>();
                for (Integer productId : newProductIds) {
                        UpdateOrderDetailRequest newItem = updatedItemsMap.get(productId);
                        GetProductDetailResponse productDetail = productDetailsMap.get(productId);
                        Integer currentStock = productDetail.getStockQty();
                        Integer quantityToAdd = newItem.getQuantity();

                        // 計算新庫存 (oldQuantity 為 0)
                        Integer newStock = calculateNewStock(
                                        productId,
                                        currentStock,
                                        0, // oldQuantity for new item is 0
                                        quantityToAdd);
                        stockUpdates.put(productId, newStock);

                        // 建立新的 OrderDetail 實體
                        OrderDetail newDetail = new OrderDetail(existingOrderInfo, productId, quantityToAdd);
                        orderDetailsToAdd.add(newDetail);

                        logger.info("訂單 {} 新增商品項目：產品ID {}, 數量 {}",
                                        orderId, productId, quantityToAdd);
                }

                // 9. 處理移除項目
                List<OrderDetail> orderDetailsToRemove = new ArrayList<>();
                for (Integer productId : removedProductIds) {
                        OrderDetail detailToRemove = existingDetailsMap.get(productId);
                        GetProductDetailResponse productDetail = productDetailsMap.get(productId); // 需要確保
                                                                                                   // productDetailsMap
                                                                                                   // 包含被移除商品的資訊
                        Integer currentStock = productDetail.getStockQty();
                        Integer quantityToRestore = detailToRemove.getQuantity();

                        // 計算新庫存 (newQuantity 為 0)
                        Integer newStock = calculateNewStock(
                                        productId,
                                        currentStock,
                                        quantityToRestore, // oldQuantity is the quantity being removed
                                        0); // newQuantity is 0
                        stockUpdates.put(productId, newStock);

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

                        if (!oldQuantity.equals(newQuantity)) {
                                GetProductDetailResponse productDetail = productDetailsMap.get(productId);
                                Integer currentStock = productDetail.getStockQty();

                                // 計算新庫存
                                Integer newStock = calculateNewStock(
                                                productId,
                                                currentStock,
                                                oldQuantity,
                                                newQuantity);
                                stockUpdates.put(productId, newStock);

                                existingDetail.setQuantity(newQuantity);
                                orderDetailsToUpdate.add(existingDetail);

                                logger.info("訂單 {} 商品項目產品ID {} 數量由 {} 更新為 {}",
                                                orderId, productId, oldQuantity, newQuantity);
                        }
                }

                // 11. 執行批量更新
                batchUpdateProductStock(stockUpdates);

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
                                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

                logger.info("找到要刪除的訂單，ID: {}", orderId);

                // 2. 驗證訂單狀態
                if (existingOrderInfo.getStatus() != 1001) {
                        throw new InvalidRequestException("訂單狀態不允許刪除，目前狀態: " + existingOrderInfo.getStatus());
                }

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

                        // 計算新庫存 (newQuantity 為 0)
                        Integer newStock = calculateNewStock(
                                        productId,
                                        currentStock,
                                        quantityToRestore, // oldQuantity is the quantity being restored
                                        0); // newQuantity is 0
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
                        throw new IllegalArgumentException(
                                        "Product ID, current stock, old quantity, and new quantity cannot be null.");
                }
                if (originalQuantity < 0 || requestQuantity < 0) {
                        throw new IllegalArgumentException(
                                        "Order quantities cannot be negative for product ID: " + productId);
                }
                if (currentStock < 0) {
                        // 理論上 currentStock 不應為負，但加上just in case
                        logger.warn("Current stock is negative for product ID: {}. Stock: {}", productId, currentStock);
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
                        logger.error("Stock insufficient for product ID: {}. Current: {}, Old Qty: {}, New Qty: {}, Required Change: {}, Potential New Stock: {}",
                                        productId, currentStock, originalQuantity, requestQuantity, netChange,
                                        newQuantity);
                        // 拋出更詳細的錯誤訊息
                        throw new IllegalArgumentException(String.format(
                                        "商品 %d 庫存不足。目前庫存: %d, 訂單原數量: %d, 訂單新數量: %d。需要額外 %d 個，但庫存不足。",
                                        productId, currentStock, originalQuantity, requestQuantity, netChange));
                }

                logger.debug("Calculated stock for product ID: {}. Current: {}, Old Qty: {}, New Qty: {}, Net Change Required: {}, New Stock: {}",
                                productId, currentStock, originalQuantity, requestQuantity, netChange,
                                newQuantity);

                return newQuantity;
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

        public void validateAccountExist(Integer accountId) {
                accountClient.validateAccountExist(accountId);
        }        

        // 驗證帳戶ID有無關聯的訂單
        public boolean AccountIdIsInOrder(Integer accountId){
                if(!orderInfoRepository.findByAccountId(accountId).isEmpty()){
                        return true;
                }
                return false;
        }
        
}