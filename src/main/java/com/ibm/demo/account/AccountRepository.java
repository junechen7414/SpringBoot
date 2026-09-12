package com.ibm.demo.account;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.util.SoftDeleteRepository;

public interface AccountRepository extends JpaRepository<Account, Integer>, SoftDeleteRepository<Integer> {
    List<Account> findByStatus(String status);

    @Query("SELECT a FROM Account a")
    List<Account> findAllAccount();

    @Query("SELECT a FROM Account a")
    Page<Account> findAllAccount(Pageable pageable);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    // 寫入的狀態值由 AccountStatus 串接而來，不硬編字面值：JPQL 是 annotation 裡的字串，
    // 編譯器不會檢查它，改了列舉而漏改這裡的話會靜靜地把帳戶寫成錯誤狀態。
    // 用一般字串串接而非 text block，是因為 text block 中間插常數會難讀到反而更容易寫錯。
    @Query("UPDATE Account a SET a.softDeleteMetadata.deleted = true, "
            + "a.softDeleteMetadata.deletedAt = CURRENT_TIMESTAMP, "
            + "a.status = '" + AccountStatus.Codes.INACTIVE + "', "
            + "a.version = a.version + 1 "
            + "WHERE a.id = :id AND a.version = :version")
    int softDeleteById(@Param("id") Integer id, @Param("version") Integer version);
}
