package com.ibm.demo.product;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.util.SoftDeleteRepository;

public interface ProductRepository extends JpaRepository<Product, Integer>, SoftDeleteRepository<Integer> {
    List<Product> findBySaleStatus(Integer saleStatus);

    @Query("SELECT p FROM Product p")
    List<Product> findAllProducts();

    @Query("SELECT p FROM Product p")
    Page<Product> findAllProducts(Pageable pageable);

    @Modifying
    @Query("UPDATE Product p SET p.available = p.available - :qty, p.reserved = p.reserved + :qty WHERE p.id = :productId AND p.available >= :qty")
    Integer reserveProduct(Integer productId, Integer qty);

    @Modifying
    @Query("UPDATE Product p SET p.available = p.available + :qty, p.reserved = p.reserved - :qty WHERE p.id = :productId AND p.reserved >= :qty")
    Integer releaseProduct(Integer productId, Integer qty);

    // 存在性(且可銷售)檢查：僅投影 id，不水合整個 Product entity，避免載入 PC 後 stale。
    // 受 @SQLRestriction("DELETED = false AND SALE_STATUS = 1001") 限制，故等同「存在且可銷售」。
    @Query("SELECT p.id FROM Product p WHERE p.id IN :ids")
    List<Integer> findExistingIds(@Param("ids") Collection<Integer> ids);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Product p SET p.softDeleteMetadata.deleted = true,
            p.softDeleteMetadata.deletedAt = CURRENT_TIMESTAMP,
            p.saleStatus = 1002,
            p.version = p.version + 1
            WHERE p.id = :id AND p.version = :version
            """)
    // 確保 @Param 名稱與 Query 中的 :名稱 一致
    int softDeleteById(@Param("id") Integer id, @Param("version") Integer version);

    boolean existsByName(String name);
}
