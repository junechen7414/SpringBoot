package com.ibm.demo.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ibm.demo.product.DTO.GetProductListResponse;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    @Query("SELECT new com.ibm.demo.product.DTO.GetProductListResponse(p.id, p.name, p.price, p.saleStatus, p.available) FROM Product p WHERE p.saleStatus = :saleStatus")
    List<GetProductListResponse> findBySaleStatus(Integer saleStatus);

    @Query("SELECT new com.ibm.demo.product.DTO.GetProductListResponse(p.id, p.name, p.price, p.saleStatus, p.available) FROM Product p")
    List<GetProductListResponse> findAllProducts();

    @Modifying
    @Query("UPDATE Product p SET p.available = p.available - :qty, p.reserved = p.reserved + :qty WHERE p.id = :productId AND p.available >= :qty")
    Integer reserveProduct(Integer productId, Integer qty);

    @Modifying
    @Query("UPDATE Product p SET p.available = p.available + :qty, p.reserved = p.reserved - :qty WHERE p.id = :productId AND p.reserved >= :qty")
    Integer releaseProduct(Integer productId, Integer qty);

    // 提供未來可能的擴充功能: 確認預留（實際扣庫存)，可能是支付/確認出單後的操作
    @Modifying
    @Query("UPDATE Product p SET p.reserved = p.reserved - :qty WHERE p.id = :productId AND p.reserved >= :qty")
    Integer confirmReservation(Integer productId, Integer qty);

    boolean existsByName(String name);
}
