package com.ibm.demo.order;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.OrderDeletionPlan;
import com.ibm.demo.order.DTO.OrderView;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;
import com.ibm.demo.util.DBAssertion;
import com.ibm.demo.util.ErrorCode;

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

                // 明細先掛到父端（addOrderDetail 同步雙向兩端），再以單一 save 由 cascade PERSIST 一併寫入。
                // 舊寫法只設子端的 orderInfo 再另外 saveAll，交易內父端集合會是空的（雙向不一致）。
                createOrderRequest.items().forEach(detailRequest -> newOrderInfo.addOrderDetail(
                                OrderDetail.builder()
                                                .productId(detailRequest.productId())
                                                .quantity(detailRequest.quantity())
                                                .build()));

                OrderInfo savedOrderInfo = orderInfoRepository.save(newOrderInfo);

                log.info("訂單建立成功，訂單ID: {}, 帳戶ID: {}", 
                        savedOrderInfo.getId(), 
                        createOrderRequest.accountId());
                return savedOrderInfo.getId();
        }

        @Transactional
        public void updateOrder(UpdateOrderRequest request) {
                log.debug("開始更新訂單，訂單ID: {}, 新狀態: {}, 商品數量: {}",
                        request.orderId(),
                        request.orderStatus(),
                        request.items().size());

                // 交易內自行載入(managed)：關閉 OSIV 後不可沿用外部傳入的 detached entity，否則
                // 存取 lazy orderDetails 仍會拋 LazyInitializationException。
                OrderInfo order = orderInfoRepository.findById(request.orderId()).orElseThrow(
                                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                                "Order not found with ID: " + request.orderId()));

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
                                .forEach(item -> order.addOrderDetail(
                                                OrderDetail.builder()
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
         * 交易內載入單一訂單並萃取為 {@link OrderView} 純快照(含 lazy 明細)。
         * 供讀取/更新端點在交易外安全使用，不依賴 OSIV。找不到則拋 BusinessException（RESOURCE_NOT_FOUND）。
         */
        @Transactional(readOnly = true)
        public OrderView loadOrderView(Integer orderId) {
                OrderInfo order = orderInfoRepository.findById(orderId).orElseThrow(
                                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                                "Order not found with ID: " + orderId));
                return toView(order);
        }

        /**
         * 交易內分頁載入某帳戶的訂單並各自萃取為 {@link OrderView}。lazy 明細於 session 內載入
         * (搭配 OrderInfo.orderDetails 的 @BatchSize 緩解 N+1)，回傳 detached-safe 的純快照頁。
         */
        @Transactional(readOnly = true)
        public Page<OrderView> loadOrderViews(Integer accountId, Pageable pageable) {
                return orderInfoRepository.findByAccountId(accountId, pageable).map(this::toView);
        }

        /** 在交易/session 內把 OrderInfo(含 lazy orderDetails)轉為純快照。 */
        private OrderView toView(OrderInfo order) {
                List<OrderItemRequest> items = order.getOrderDetails().stream()
                                .map(detail -> OrderItemRequest.builder()
                                                .productId(detail.getProductId())
                                                .quantity(detail.getQuantity())
                                                .build())
                                .collect(Collectors.toList());
                return new OrderView(order.getId(), order.getAccountId(), order.getStatus(), items);
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
                                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                                "Order not found with ID: " + orderId));

                if (order.getStatus() != OrderStatus.CREATED.getCode()) {
                        throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                                        "訂單狀態不允許刪除，目前狀態: " + order.getStatus());
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