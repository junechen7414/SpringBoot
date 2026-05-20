package com.ibm.demo.order.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.order.Entity.OrderDetail;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Integer> {
    @Modifying
    @Query("""
            UPDATE OrderDetail d
            SET d.softDeleteMetadata.deleted = true,
                d.softDeleteMetadata.deletedAt = CURRENT_TIMESTAMP,
                d.version = d.version + 1
            WHERE d.orderInfo.id = :orderId
            """)
    void softDeleteByOrderId(@Param("orderId") Integer orderId);
}
