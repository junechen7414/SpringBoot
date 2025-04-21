package com.ibm.demo.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Entity.OrderProductDetail;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.order.Repository.OrderProductDetailRepository;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import jakarta.transaction.Transactional;

@Service
public class OrderService {
        private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
        private OrderInfoRepository orderInfoRepository;
        private AccountRepository accountRepository;
        private OrderProductDetailRepository orderProductDetailRepository;
        private ProductRepository productRepository;

        public OrderService(OrderInfoRepository orderInfoRepository, AccountRepository accountRepository,
                        OrderProductDetailRepository orderProductDetailRepository,
                        ProductRepository productRepository) {
                this.orderInfoRepository = orderInfoRepository;
                this.accountRepository = accountRepository;
                this.orderProductDetailRepository = orderProductDetailRepository;
                this.productRepository = productRepository;
        }

        @Transactional
        public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
                int accountId = createOrderRequest.getAccountId();
                // 找到帳戶，找不到則拋出 RuntimeException，由 @Transactional 處理回滾
                Account existingAccount = accountRepository.findById(accountId)
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
                        OrderProductDetail newOrderDetail = new OrderProductDetail(savedOrderInfo, existingProduct,
                                        requestQuantity);
                        orderProductDetailRepository.save(newOrderDetail);
                        logger.info("已建立新的 OrderProductDetail，ID: {}", newOrderDetail.getId());

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
                Account existingAccount = accountRepository.findById(accountId)
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
                OrderInfo existingOrderInfo = orderInfoRepository.findById(orderId)
                                .orElseThrow(() -> new NullPointerException("Order not found with id: " + orderId));
                List<OrderProductDetail> orderDetails = existingOrderInfo.getOrderDetails();
                GetOrderDetailResponse getOrderDetailResponse = new GetOrderDetailResponse();
                getOrderDetailResponse.setAccountId(existingOrderInfo.getAccount().getId());
                getOrderDetailResponse.setOrderStatus(existingOrderInfo.getStatus());
                getOrderDetailResponse.setTotalAmount(existingOrderInfo.getTotalAmount());
                getOrderDetailResponse.setCreateDate(existingOrderInfo.getCreateDate());
                getOrderDetailResponse.setModifiedDate(existingOrderInfo.getModifiedDate());

                List<OrderItemDTO> itemDTOs = new ArrayList<>();

                for (OrderProductDetail orderDetail : orderDetails) {
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
        public void deleteOrder(int orderId) {
        }

}
