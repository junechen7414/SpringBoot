package com.ibm.demo.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ibm.demo.product.DTO.GetProductListResponse;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    @Query("SELECT new com.ibm.demo.product.DTO.GetProductListResponse(p.id, p.name, p.price, p.saleStatus, p.stockQty) FROM Product p WHERE p.saleStatus = :saleStatus")
    List<GetProductListResponse> findBySaleStatus(Integer saleStatus);

    @Query("SELECT new com.ibm.demo.product.DTO.GetProductListResponse(p.id, p.name, p.price, p.saleStatus, p.stockQty) FROM Product p")
    List<GetProductListResponse> findAllProducts();

    boolean existsByName(String name);
}
