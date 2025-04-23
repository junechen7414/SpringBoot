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

import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
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
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import jakarta.transaction.Transactional;

@Service
public class OrderService {
        private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
        private OrderInfoRepository orderInfoRepository;
        private AccountRepository accountRepository;
        private OrderDetailRepository orderDetailRepository;
        private ProductRepository productRepository;

        public OrderService(OrderInfoRepository orderInfoRepository, AccountRepository accountRepository,
                        OrderDetailRepository orderDetailRepository,
                        ProductRepository productRepository) {
                this.orderInfoRepository = orderInfoRepository;
                this.accountRepository = accountRepository;
                this.orderDetailRepository = orderDetailRepository;
                this.productRepository = productRepository;
        }

        @Transactional
        public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
                int accountId = createOrderRequest.getAccountId();
                // 找到帳戶，找不到則拋出 RuntimeException，由 @Transactional 處理回滾
                Account existingAccount = accountRepository.findByIdWithOrders(accountId)
                                .orElseThrow(() -> new NullPointerException("Account not found with id:" + accountId));
                logger.info("找到帳戶，ID: {}", accountId);

                OrderInfo newOrderInfo = new OrderInfo(existingAccount);
                // total amount 預設值給 0
                BigDecimal totalAmount = BigDecimal.ZERO;
                newOrderInfo.setTotalAmount(totalAmount);

                // status 預設值為 1001
                newOrderInfo.setStatus(1001);

                // 儲存 OrderInfo，如發生錯誤 (RuntimeException)，會由 @Transactional 處理回滾
                OrderInfo savedOrderInfo = orderInfoRepository.save(newOrderInfo);
                logger.info("已建立新的 OrderInfo，ID: {}", savedOrderInfo.getId());

                for (CreateOrderDetailRequest detailRequest : createOrderRequest.getOrderDetails()) {
                        int requestProductId = detailRequest.getProductId();
                        int requestQuantity = detailRequest.getQuantity();
                        logger.info("處理商品 ID: {}，數量: {}", requestProductId, requestQuantity);

                        // 找到商品，找不到則拋出 RuntimeException，由 @Transactional 處理回滾
                        Product existingProduct = productRepository.findById(requestProductId)
                                        .orElseThrow(() -> new NullPointerException(
                                                        "Product not found with id:" + requestProductId));
                        logger.info("找到商品，ID: {}", requestProductId);

                        BigDecimal productPrice = existingProduct.getPrice();
                        int newStockQuantity = existingProduct.getStockQty() - requestQuantity;

                        // 檢查庫存，不足則拋出 RuntimeException，由 @Transactional 處理回滾
                        if (newStockQuantity < 0) {
                                throw new IllegalArgumentException("商品" + requestProductId + "庫存不足");
                        }

                        // 更新商品庫存，如發生錯誤 (RuntimeException)，由 @Transactional 處理回滾
                        existingProduct.setStockQty(newStockQuantity);
                        productRepository.save(existingProduct); // Spring Data JPA save
                        logger.info("已扣減商品 ID {} 庫存。新庫存: {}", requestProductId, newStockQuantity);

                        // 建立訂單明細，如發生錯誤 (RuntimeException)，由 @Transactional 處理回滾
                        OrderDetail newOrderDetail = new OrderDetail(savedOrderInfo, existingProduct,
                                        requestQuantity);
                        orderDetailRepository.save(newOrderDetail);
                        logger.info("已建立新的 OrderDetail，ID: {}", newOrderDetail.getId());

                        BigDecimal subTotalAmount = productPrice.multiply(BigDecimal.valueOf(requestQuantity));
                        totalAmount = totalAmount.add(subTotalAmount);
                        logger.info("商品 ID {} 小計金額: {}", requestProductId, subTotalAmount);
                }

                // 更新訂單總金額，如發生錯誤 (RuntimeException)，由 @Transactional 處理回滾
                savedOrderInfo.setTotalAmount(totalAmount);
                orderInfoRepository.save(savedOrderInfo);
                logger.info("已更新 Order ID {} 的總金額為 {}", savedOrderInfo.getId(), totalAmount);

                // 設定返回結果
                CreateOrderResponse response = new CreateOrderResponse(savedOrderInfo.getId(), accountId,
                                savedOrderInfo.getStatus(), savedOrderInfo.getTotalAmount(),
                                savedOrderInfo.getCreateDate());

                return response;
        }

        public List<GetOrderListResponse> getOrderList(int accountId) {
                Account existingAccount = accountRepository.findByIdWithOrders(accountId)
                                .orElseThrow(() -> new NullPointerException("Account not found with id: " + accountId));
                List<OrderInfo> orderInfoList = existingAccount.getOrders();
                List<GetOrderListResponse> getOrderListResponse = new ArrayList<>();
                for (OrderInfo orderInfo : orderInfoList) {
                        GetOrderListResponse response = new GetOrderListResponse();
                        response.setOrderId(orderInfo.getId());
                        response.setStatus(orderInfo.getStatus());
                        response.setTotalAmount(orderInfo.getTotalAmount());
                        response.setCreateDate(orderInfo.getCreateDate());
                        getOrderListResponse.add(response);
                }

                return getOrderListResponse;
        }

        public GetOrderDetailResponse getOrderDetail(int orderId) {
                OrderInfo existingOrderInfo = orderInfoRepository.findByIdWithOrderDetailsAndProductAndAccount(orderId)
                                .orElseThrow(() -> new NullPointerException("Order not found with id: " + orderId));
                List<OrderDetail> orderDetails = existingOrderInfo.getOrderDetails();
                GetOrderDetailResponse getOrderDetailResponse = new GetOrderDetailResponse();
                getOrderDetailResponse.setAccountId(existingOrderInfo.getAccount().getId());
                getOrderDetailResponse.setOrderStatus(existingOrderInfo.getStatus());
                getOrderDetailResponse.setTotalAmount(existingOrderInfo.getTotalAmount());
                getOrderDetailResponse.setCreateDate(existingOrderInfo.getCreateDate());
                getOrderDetailResponse.setModifiedDate(existingOrderInfo.getModifiedDate());

                List<OrderItemDTO> itemDTOs = new ArrayList<>();

                for (OrderDetail orderDetail : orderDetails) {
                        OrderItemDTO itemDTO = new OrderItemDTO();
                        Product singleProduct = orderDetail.getProduct();
                        itemDTO.setProductId(singleProduct.getId());
                        itemDTO.setProductName(singleProduct.getName());
                        itemDTO.setQuantity(orderDetail.getQuantity());
                        itemDTO.setProductPrice(singleProduct.getPrice());
                        itemDTOs.add(itemDTO);
                }

                getOrderDetailResponse.setItems(itemDTOs);

                return getOrderDetailResponse;
        }

        @Transactional
        public UpdateOrderResponse updateOrder(UpdateOrderRequest updateOrderRequest) {
                int orderId = updateOrderRequest.getOrderId();
                OrderInfo existingOrderInfo = orderInfoRepository.findByIdWithOrderDetailsAndProduct(orderId)
                                .orElseThrow(() -> new NullPointerException("Order not found with id: " + orderId));
                logger.info("找到要更新的訂單，ID: {}", orderId);

                int orderStatus = existingOrderInfo.getStatus();
                if (orderStatus != 1001) {
                        throw new IllegalArgumentException("訂單狀態不允許更新商品項目，目前狀態: " + orderStatus);
                }
                logger.info("訂單狀態 {} 允許更新商品項目", orderStatus);

                // 建立現有明細和更新明細的Map以便比較
                Map<Integer, OrderDetail> existingDetailsMap = new HashMap<>();
                for (OrderDetail detail : existingOrderInfo.getOrderDetails()) {
                        Integer productId = detail.getProduct().getId(); // 取得產品的 ID 作為 Map 的 Key
                        existingDetailsMap.put(productId, detail); // 將產品 ID 和訂單明細物件放入 Map 中
                }

                Map<Integer, UpdateOrderDetailRequest> updatedItemsMap = new HashMap<>();
                for (UpdateOrderDetailRequest detailRequest : updateOrderRequest.getItems()) {
                        Integer productId = detailRequest.getProductId();
                        updatedItemsMap.put(productId, detailRequest);
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

                // 將取出的產品ID Set對應到要新增的明細
                List<UpdateOrderDetailRequest> itemsToAdd = new ArrayList<>();
                for (Integer productId : newProductIds) {
                        itemsToAdd.add(updatedItemsMap.get(productId));
                }

                // 新增 itemsToAdd 到訂單的明細中
                for (UpdateOrderDetailRequest detailRequest : itemsToAdd) {
                        Product product = productRepository.findById(detailRequest.getProductId())
                                        .orElseThrow(() -> new NullPointerException(
                                                        "Product not found with id: " + detailRequest.getProductId()));

                        if (product.getStockQty() < detailRequest.getQuantity()) {
                                throw new IllegalArgumentException("商品" + detailRequest.getProductId() + "庫存不足");
                        }

                        OrderDetail newDetail = new OrderDetail();
                        newDetail.setOrderInfo(existingOrderInfo);
                        newDetail.setProduct(product);
                        newDetail.setQuantity(detailRequest.getQuantity());

                        existingOrderInfo.addOrderDetail(newDetail); // 將新的明細加到訂單的明細列表中
                        orderDetailRepository.save(newDetail);

                        product.setStockQty(product.getStockQty() - detailRequest.getQuantity());
                        productRepository.save(product);

                        logger.info("訂單 {} 新增商品項目：產品ID {}, 數量 {}", orderId, detailRequest.getProductId(),
                                        detailRequest.getQuantity());
                }

                // 將取出的產品ID Set對應到要移除的明細
                List<OrderDetail> itemsToRemove = new ArrayList<>();
                for (Integer productId : removedProductIds) {
                        itemsToRemove.add(existingDetailsMap.get(productId));
                }

                // 從訂單的各明細中移除 itemsToRemove
                for (OrderDetail detailToRemove : itemsToRemove) {
                        Product product = productRepository.findById(detailToRemove.getProduct().getId())
                                        .orElseThrow(() -> new NullPointerException(
                                                        "Product not found with id: "
                                                                        + detailToRemove.getProduct().getId()));

                        product.setStockQty(product.getStockQty() + detailToRemove.getQuantity());
                        productRepository.save(product);

                        existingOrderInfo.removeOrderDetail(detailToRemove); // 將明細從訂單的明細列表中移除
                        orderDetailRepository.delete(detailToRemove);
                        logger.info("訂單 {} 移除商品項目：產品ID {}, 數量 {}", orderId, product.getId(),
                                        detailToRemove.getQuantity());
                }

                // 分類本來就在訂單明細中的產品是有更新or not
                for (Integer productId : commonProductIds) {
                        OrderDetail existingDetail = existingDetailsMap.get(productId);
                        UpdateOrderDetailRequest updatedRequest = updatedItemsMap.get(productId);
                        int oldQuantity = existingDetail.getQuantity();
                        int newQuantity = updatedRequest.getQuantity();
                        // 只有數量有變動的明細才需要更新，沒變動就不更新
                        if (oldQuantity != newQuantity) {
                                int quantityDifference = updatedRequest.getQuantity() - existingDetail.getQuantity();
                                existingDetail.setQuantity(updatedRequest.getQuantity());

                                logger.info("訂單 {} 商品項目產品ID {} 數量從 {} 更新為 {}",
                                                orderId, productId, oldQuantity, newQuantity);
                                Product product = existingDetail.getProduct();

                                // 如果quantityDifference > 0 ，商品庫存減去更新數量差要判斷是否超出庫存，else可以拿庫存減去更新的數量差；
                                // 如果quantityDifference < 0，商品庫存減去更新數量差會負負得正，於是只判斷有無超出庫存

                                // 檢查是否超出庫存
                                if (product.getStockQty() < quantityDifference) {
                                        throw new IllegalArgumentException("商品" + productId + "庫存不足");
                                }

                                // 更新商品庫存並儲存
                                product.setStockQty(product.getStockQty() - quantityDifference);
                                productRepository.save(product);

                                // 更新訂單明細並儲存
                                existingDetail.setQuantity(newQuantity);
                                orderDetailRepository.save(existingDetail);

                                logger.info("訂單 {} 商品項目產品ID {} 數量由 {} 更新為 {}，庫存更新完成",
                                                orderId, productId, oldQuantity, newQuantity);
                        }
                }

                // 重新計算訂單總金額
                BigDecimal totalAmount = BigDecimal.ZERO;
                // totalAmount = existingOrderInfo.calculateTotalAmount();
                for (OrderDetail detail : existingOrderInfo.getOrderDetails()) {
                        BigDecimal productPrice = detail.getProduct().getPrice();
                        int quantity = detail.getQuantity();
                        totalAmount = totalAmount.add(productPrice.multiply(BigDecimal.valueOf(quantity)));
                }
                existingOrderInfo.setTotalAmount(totalAmount);
                orderInfoRepository.save(existingOrderInfo);


                UpdateOrderResponse response = new UpdateOrderResponse();
                response.setOrderId(existingOrderInfo.getId());
                response.setTotalAmount(existingOrderInfo.getTotalAmount());
                List<UpdateOrderDetailResponse> itemsResponse = new ArrayList<>();

                for (OrderDetail detail : existingOrderInfo.getOrderDetails()) {
                        UpdateOrderDetailResponse itemResponse = new UpdateOrderDetailResponse();
                        itemResponse.setProductId(detail.getProduct().getId());
                        itemResponse.setQuantity(detail.getQuantity());
                        itemsResponse.add(itemResponse);
                }

                response.setItems(itemsResponse);

                return response;

        }

        @Transactional
        public void deleteOrder(int orderId) {
        }

}
