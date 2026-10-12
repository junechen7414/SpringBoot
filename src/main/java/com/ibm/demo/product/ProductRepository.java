package com.ibm.demo.product;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.util.SoftDeleteRepository;

public interface ProductRepository extends JpaRepository<Product, Integer>, SoftDeleteRepository<Integer> {
    List<Product> findBySaleStatus(Integer saleStatus);

    @Query("SELECT p FROM Product p")
    List<Product> findAllProducts();

    @Query("SELECT p FROM Product p")
    Page<Product> findAllProducts(Pageable pageable);

    // bulk UPDATE 不會自動遞增 @Version，要自己加 1。否則持有舊 version 的寫入看不出庫存已被動過：
    // Delist 檢查完保留量後，若有並發的 reserve，軟刪除仍會命中 version 而靜默成功；
    // PUT 也會用讀到的 available 蓋掉這次預留。reserve 本身不檢查 version，所以不會因此多出衝突。
    @Modifying
    @Query("UPDATE Product p SET p.available = p.available - :qty, p.reserved = p.reserved + :qty, "
            + "p.version = p.version + 1 "
            + "WHERE p.id = :productId AND p.available >= :qty")
    Integer reserveProduct(Integer productId, Integer qty);

    @Modifying
    @Query("UPDATE Product p SET p.available = p.available + :qty, p.reserved = p.reserved - :qty, "
            + "p.version = p.version + 1 "
            + "WHERE p.id = :productId AND p.reserved >= :qty")
    Integer releaseProduct(Integer productId, Integer qty);

    // 存在性(且可銷售)檢查：僅投影 id，不載入整個 Product entity。
    // 受 @SQLRestriction("DELETED = false AND SALE_STATUS = 1001") 限制，故等同「存在且可銷售」。
    @Query("SELECT p.id FROM Product p WHERE p.id IN :ids")
    List<Integer> findExistingIds(@Param("ids") Collection<Integer> ids);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    // 寫入的狀態值由 ProductStatus 串接而來，理由見 AccountRepository 上的同款註解。
    @Query("UPDATE Product p SET p.softDeleteMetadata.deleted = true, "
            + "p.softDeleteMetadata.deletedAt = CURRENT_TIMESTAMP, "
            + "p.saleStatus = " + ProductStatus.Codes.UNAVAILABLE + ", "
            + "p.version = p.version + 1 "
            + "WHERE p.id = :id AND p.version = :version")
    // 確保 @Param 名稱與 Query 中的 :名稱 一致
    int softDeleteById(@Param("id") Integer id, @Param("version") Integer version);

    boolean existsByName(String name);
}
