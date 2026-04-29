package com.ibm.demo.order.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.order.Entity.OrderInfo;

public interface OrderInfoRepository extends JpaRepository<OrderInfo, Integer> {
    // @Query("SELECT o FROM OrderInfo o WHERE o.accountId = :accountId")
    List<OrderInfo> findByAccountId(@Param("accountId") Integer accountId);

    @Modifying
    @Query("""
            UPDATE OrderInfo o
            SET o.deleted = true,
                o.deletedAt = CURRENT_TIMESTAMP,
                o.status = 1003,
                o.version = o.version + 1
            WHERE o.id = :id AND o.version = :version
            """)
    int softDeleteByOrderId(@Param("id") Integer id, @Param("version") Integer version);
}
