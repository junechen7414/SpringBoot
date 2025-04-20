package com.ibm.demo.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    @Query("SELECT ProductListResponseDTO(p.id, p.name, p.price) FROM Product p")
    List<ProductListResponseDTO> getProductList();
}
