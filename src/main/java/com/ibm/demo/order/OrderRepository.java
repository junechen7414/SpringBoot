package com.ibm.demo.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.util.SoftDeleteRepository;

public interface OrderRepository extends JpaRepository<Order, Integer>, SoftDeleteRepository<Integer> {

    // 必須是 LEFT JOIN FETCH：inner join 會要求「至少一筆明細」才回傳訂單。訂單可能沒有任何明細
    // （例如資料被手動清過，或日後放寬 updateOrder 的 @NotEmpty），用 inner join 的話這種訂單會查不到、
    // 對外誤報 404 ——「查無訂單」與「訂單沒有明細」是兩件事。
    @Query("SELECT o FROM CustomerOrder o LEFT JOIN FETCH o.orderLines WHERE o.id = :id")
    Optional<Order> findByIdWithLines(@Param("id") Integer id);

    List<Order> findByAccountId(@Param("accountId") Integer accountId);

    Page<Order> findByAccountId(@Param("accountId") Integer accountId, Pageable pageable);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CustomerOrder o "
            + "SET o.softDeleteMetadata.deleted = true, "
            + "    o.softDeleteMetadata.deletedAt = :now, "
            + "    o.auditMetadata.updatedAt = :now, "
            + "    o.version = o.version + 1 "
            + "WHERE o.id = :id AND o.version = :version")
    int softDeleteById(@Param("id") Integer id, @Param("version") Integer version,
            @Param("now") LocalDateTime now);
}
