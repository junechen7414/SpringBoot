package com.ibm.demo.order.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ibm.demo.order.Entity.OrderInfo;

public interface OrderInfoRepository extends JpaRepository<OrderInfo, Integer> {
    
}
