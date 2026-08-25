package com.ibm.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.order.DTO.OrderDeletionPlan;
import com.ibm.demo.order.DTO.OrderView;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;
import com.ibm.demo.exception.ErrorCode;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
public class OrderTransactionalServiceTest {

    @Mock
    private OrderInfoRepository orderInfoRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    private OrderTransactionalService orderTransactionalService;

    @BeforeEach
    void setUp() {
        orderTransactionalService = new OrderTransactionalService(orderInfoRepository, orderDetailRepository);
    }

    @Test
    @DisplayName("刪除訂單時若版本不符 (softDeleteById 命中 0 列)，應拋出 ObjectOptimisticLockingFailureException")
    void deleteOrder_WhenOptimisticLockingConflict_ShouldThrowException() {
        // Arrange
        Integer orderId = 1;
        Integer version = 1;
        when(orderInfoRepository.softDeleteById(orderId, version)).thenReturn(0);

        // Act & Assert
        assertThatThrownBy(() -> orderTransactionalService.deleteOrder(orderId, version))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(orderInfoRepository).softDeleteById(orderId, version);
    }

    @Test
    @DisplayName("prepareOrderDeletion 應載入 CREATED 訂單，並在交易內把明細萃取為 items DTO")
    void prepareOrderDeletion_Success() {
        // Arrange
        OrderInfo order = new OrderInfo();
        order.setId(1);
        order.setAccountId(42);
        order.setStatus(OrderStatus.CREATED.getCode());
        order.setVersion(3);
        order.setOrderDetails(List.of(
                OrderDetail.builder().productId(10).quantity(2).build(),
                OrderDetail.builder().productId(11).quantity(5).build()));
        when(orderInfoRepository.findById(1)).thenReturn(Optional.of(order));

        // Act
        OrderDeletionPlan plan = orderTransactionalService.prepareOrderDeletion(1);

        // Assert
        assertThat(plan.accountId()).isEqualTo(42);
        assertThat(plan.version()).isEqualTo(3);
        assertThat(plan.items()).containsExactlyInAnyOrder(
                new OrderItemRequest(10, 2),
                new OrderItemRequest(11, 5));
    }

    @Test
    @DisplayName("prepareOrderDeletion 若訂單不存在，應拋出 ResourceNotFoundException")
    void prepareOrderDeletion_WhenNotFound_ShouldThrow() {
        // Arrange
        when(orderInfoRepository.findById(99)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderTransactionalService.prepareOrderDeletion(99))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND)
                .hasMessageContaining("Order not found")
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("prepareOrderDeletion 若狀態非 CREATED，應拋出 OrderStatusInvalidException")
    void prepareOrderDeletion_WhenStatusNotCreated_ShouldThrow() {
        // Arrange
        OrderInfo order = new OrderInfo();
        order.setId(1);
        order.setStatus(OrderStatus.CANCELLED.getCode());
        when(orderInfoRepository.findById(1)).thenReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderTransactionalService.prepareOrderDeletion(1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_STATUS_INVALID)
                .hasMessageContaining("訂單狀態不允許刪除");
    }

    @Test
    @DisplayName("loadOrderView 應載入訂單並把明細萃取為 items 快照")
    void loadOrderView_Success() {
        // Arrange
        OrderInfo order = new OrderInfo();
        order.setId(7);
        order.setAccountId(3);
        order.setStatus(OrderStatus.CREATED.getCode());
        order.setOrderDetails(List.of(
                OrderDetail.builder().productId(20).quantity(4).build()));
        when(orderInfoRepository.findByIdWithDetails(7)).thenReturn(Optional.of(order));

        // Act
        OrderView view = orderTransactionalService.loadOrderView(7);

        // Assert
        assertThat(view.orderId()).isEqualTo(7);
        assertThat(view.accountId()).isEqualTo(3);
        assertThat(view.status()).isEqualTo(OrderStatus.CREATED.getCode());
        assertThat(view.items()).containsExactly(new OrderItemRequest(20, 4));
    }

    @Test
    @DisplayName("loadOrderView 若訂單不存在應拋出 ResourceNotFoundException")
    void loadOrderView_WhenNotFound_ShouldThrow() {
        // Arrange
        when(orderInfoRepository.findByIdWithDetails(404)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderTransactionalService.loadOrderView(404))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND)
                .hasMessageContaining("Order not found");
    }
}
