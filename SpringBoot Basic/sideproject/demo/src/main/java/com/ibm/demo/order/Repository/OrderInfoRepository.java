package com.ibm.demo.order.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ibm.demo.order.Entity.OrderInfo;


public interface OrderInfoRepository extends JpaRepository<OrderInfo, Integer> {
    
    // @EntityGraph(attributePaths = {"orderDetails, orderDetails.product", "account"})
    // Optional<OrderInfo> findByIdWithOrderDetailsAndProductAndAccount(int id);

    // @EntityGraph(attributePaths = {"orderDetails, orderDetails.product"})
    // Optional<OrderInfo> findByIdWithOrderDetailsAndProduct(int id);

}
