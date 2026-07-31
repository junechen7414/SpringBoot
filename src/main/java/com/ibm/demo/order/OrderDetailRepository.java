package com.ibm.demo.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Integer> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE OrderDetail d
            SET d.softDeleteMetadata.deleted = true,
                d.softDeleteMetadata.deletedAt = CURRENT_TIMESTAMP,
                d.version = d.version + 1
            WHERE d.orderInfo.id = :orderId
            """)
    void softDeleteByOrderId(@Param("orderId") Integer orderId);
}
