package com.ibm.demo.order.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.order.Entity.OrderInfo;

public interface OrderInfoRepository extends JpaRepository<OrderInfo, Integer> {
    @Query("SELECT o FROM OrderInfo o WHERE o.accountId = :accountId")
    List<OrderInfo> findByAccountId(@Param("accountId") Integer accountId);

    // @EntityGraph(attributePaths = {"orderDetails, orderDetails.product",
    // "account"})
    // Optional<OrderInfo> findByIdWithOrderDetailsAndProductAndAccount(Integer id);

    // @EntityGraph(attributePaths = {"orderDetails, orderDetails.product"})
    // Optional<OrderInfo> findByIdWithOrderDetailsAndProduct(Integer id);

}
