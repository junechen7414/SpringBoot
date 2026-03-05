package com.ibm.demo.order;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.order.Entity.OrderDetail;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderTransactionalService {
    private final OrderInfoRepository orderInfoRepository;
    private final OrderDetailRepository orderDetailRepository;

    @Transactional
    public Integer createOrder(CreateOrderRequest createOrderRequest) {
        // 建立新訂單
        OrderInfo newOrderInfo = OrderInfo.builder()
                .accountId(createOrderRequest.accountId())
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

    @Transactional
    public void updateOrder(UpdateOrderRequest request, OrderInfo order) {
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
    }

    @Transactional
    public void deleteOrder(OrderInfo existingOrderInfo) {
        orderInfoRepository.delete(existingOrderInfo);
    }
}