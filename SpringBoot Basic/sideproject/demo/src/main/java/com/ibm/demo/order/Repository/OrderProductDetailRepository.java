package com.ibm.demo.order.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ibm.demo.order.Entity.OrderProductDetail;

public interface OrderProductDetailRepository extends JpaRepository<OrderProductDetail, Integer> {
    
}
