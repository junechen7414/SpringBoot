package com.ibm.demo.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderDetailRepository;
import com.ibm.demo.order.Repository.OrderInfoRepository;

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
    @DisplayName("刪除訂單時若版本不符，應拋出 ObjectOptimisticLockingFailureException")
    void deleteOrder_WhenOptimisticLockingConflict_ShouldThrowException() {
        // Arrange
        OrderInfo order = new OrderInfo();
        order.setId(1);
        Integer version = 1;

        when(orderInfoRepository.softDeleteById(1, version)).thenReturn(0);

        // Act & Assert
        assertThatThrownBy(() -> orderTransactionalService.deleteOrder(order, version))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(orderInfoRepository).softDeleteById(1, version);
    }
}
