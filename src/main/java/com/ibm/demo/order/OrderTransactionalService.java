package com.ibm.demo.order;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.exception.BusinessLogicCheck.OrderStatusInvalidException;
import com.ibm.demo.exception.BusinessLogicCheck.ResourceNotFoundException;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.OrderDeletionPlan;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.Entity.OrderDetail;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;
import com.ibm.demo.util.DBAssertion;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTransactionalService {
        private final OrderInfoRepository orderInfoRepository;
        private final OrderDetailRepository orderDetailRepository;

        @Transactional
        public Integer createOrder(CreateOrderRequest createOrderRequest) {
                log.debug("開始建立訂單，帳戶ID: {}, 商品數量: {}", 
                        createOrderRequest.accountId(), 
                        createOrderRequest.items().size());
                
                // 建立新訂單
                OrderInfo newOrderInfo = OrderInfo.builder()
                                .accountId(createOrderRequest.accountId())
                                .status(OrderStatus.CREATED.getCode())
                                .build();

                OrderInfo savedOrderInfo = orderInfoRepository.save(newOrderInfo);
                List<OrderDetail> orderDetails = createOrderRequest.items().stream()
                                .map(detailRequest -> {
                                        Integer productId = detailRequest.productId();
                                        Integer quantity = detailRequest.quantity();
                                        // 建立訂單明細
                                        return OrderDetail.builder()
                                                        .orderInfo(savedOrderInfo)
                                                        .productId(productId)
                                                        .quantity(quantity)
                                                        .build();
                                })
                                .collect(Collectors.toList());
                orderDetailRepository.saveAll(orderDetails);
                
                log.info("訂單建立成功，訂單ID: {}, 帳戶ID: {}", 
                        savedOrderInfo.getId(), 
                        createOrderRequest.accountId());
                return savedOrderInfo.getId();
        }

        @Transactional
        public void updateOrder(UpdateOrderRequest request, OrderInfo order) {
                log.debug("開始更新訂單，訂單ID: {}, 新狀態: {}, 商品數量: {}", 
                        request.orderId(), 
                        request.orderStatus(), 
                        request.items().size());
                
                Map<Integer, OrderDetail> existingMap = order.getOrderDetails().stream()
                                .collect(Collectors.toMap(OrderDetail::getProductId, Function.identity()));
                Map<Integer, UpdateOrderDetailRequest> incomingMap = request.items().stream()
                                .collect(Collectors.toMap(UpdateOrderDetailRequest::productId, Function.identity()));

                List<OrderDetail> detailsToRemove = order.getOrderDetails().stream()
                                .filter(detail -> !incomingMap.containsKey(detail.getProductId()))
                                .collect(Collectors.toList());

                order.getOrderDetails().removeAll(detailsToRemove);

                order.getOrderDetails().forEach(detail -> {
                        UpdateOrderDetailRequest incoming = incomingMap.get(detail.getProductId());
                        detail.setQuantity(incoming.quantity());
                });

                request.items().stream()
                                .filter(item -> !existingMap.containsKey(item.productId()))
                                .forEach(item -> order.getOrderDetails().add(
                                                OrderDetail.builder()
                                                                .orderInfo(order)
                                                                .productId(item.productId())
                                                                .quantity(item.quantity())
                                                                .build()));
                order.setStatus(request.orderStatus());
                orderInfoRepository.save(order);
                
                log.info("訂單更新成功，訂單ID: {}, 新狀態: {}", 
                        request.orderId(), 
                        request.orderStatus());
        }

        /**
         * 刪除前的交易內準備：載入訂單、驗證可刪狀態，並把後續步驟(遠端釋放庫存、
         * 樂觀鎖軟刪、補償告警)所需資料萃取為純 DTO 回傳。
         * <p>
         * lazy 關聯 {@code orderDetails} 的載入與轉換都發生在本 readOnly 交易/session 內，
         * 故不依賴 OSIV；呼叫端拿到的是 detached-safe 的純資料。version 於此刻擷取，
         * 用於後續軟刪的樂觀鎖檢查——釋放庫存期間若訂單被並發修改，軟刪將命中 0 列而觸發補償。
         */
        @Transactional(readOnly = true)
        public OrderDeletionPlan prepareOrderDeletion(Integer orderId) {
                OrderInfo order = orderInfoRepository.findById(orderId).orElseThrow(
                                () -> new ResourceNotFoundException("Order not found with ID: " + orderId));

                if (order.getStatus() != OrderStatus.CREATED.getCode()) {
                        throw new OrderStatusInvalidException("訂單狀態不允許刪除，目前狀態: " + order.getStatus());
                }

                // 仍在交易/session 內 → lazy 載入明細合法安全，當場轉為純 DTO
                Set<OrderItemRequest> items = order.getOrderDetails().stream()
                                .map(detail -> OrderItemRequest.builder()
                                                .productId(detail.getProductId())
                                                .quantity(detail.getQuantity())
                                                .build())
                                .collect(Collectors.toSet());

                return new OrderDeletionPlan(order.getAccountId(), order.getVersion(), items);
        }

        @Transactional
        public void deleteOrder(Integer orderId, Integer version) {
                log.debug("開始刪除訂單，訂單ID: {}", orderId);

                int updated = orderInfoRepository.softDeleteById(orderId, version);

                DBAssertion.assertUpdated(updated, OrderInfo.class, orderId);
                orderDetailRepository.softDeleteByOrderId(orderId);

                log.info("訂單刪除成功，訂單ID: {}", orderId);
        }
}