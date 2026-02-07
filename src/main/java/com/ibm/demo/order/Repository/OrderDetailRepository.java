package com.ibm.demo.order.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ibm.demo.order.Entity.OrderDetail;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Integer> {
    
}
