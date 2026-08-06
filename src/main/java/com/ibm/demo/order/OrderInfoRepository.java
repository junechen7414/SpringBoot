package com.ibm.demo.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.util.SoftDeleteRepository;

public interface OrderInfoRepository extends JpaRepository<OrderInfo, Integer>, SoftDeleteRepository<Integer> {

    // 必須是 LEFT JOIN FETCH：inner join 會要求「至少一筆存活明細」才回傳訂單，而 OrderDetail 帶
    // @SQLRestriction("DELETED = false")，該條件會併進 join 的 ON 子句。用 inner join 的話，明細全被
    // 軟刪除（或被 updateOrder 清空）的訂單會查不到，對外誤報 404 ——「查無訂單」與「訂單沒有明細」是兩件事。
    @Query("SELECT o FROM OrderInfo o LEFT JOIN FETCH o.orderDetails WHERE o.id = :id")
    Optional<OrderInfo> findByIdWithDetails(@Param("id") Integer id);

    List<OrderInfo> findByAccountId(@Param("accountId") Integer accountId);

    Page<OrderInfo> findByAccountId(@Param("accountId") Integer accountId, Pageable pageable);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE OrderInfo o
            SET o.softDeleteMetadata.deleted = true,
                o.softDeleteMetadata.deletedAt = CURRENT_TIMESTAMP,
                o.status = 1003,
                o.version = o.version + 1
            WHERE o.id = :id AND o.version = :version
            """)
    int softDeleteById(@Param("id") Integer id, @Param("version") Integer version);
}
