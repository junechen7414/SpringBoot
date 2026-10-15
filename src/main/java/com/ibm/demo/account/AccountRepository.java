package com.ibm.demo.account;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.util.SoftDeleteRepository;

public interface AccountRepository extends JpaRepository<Account, Integer>, SoftDeleteRepository<Integer> {
    @Query("SELECT a FROM Account a")
    List<Account> findAllAccount();

    @Query("SELECT a FROM Account a")
    Page<Account> findAllAccount(Pageable pageable);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Account a SET a.softDeleteMetadata.deleted = true, "
            + "a.softDeleteMetadata.deletedAt = :now, "
            + "a.auditMetadata.updatedAt = :now, "
            + "a.version = a.version + 1 "
            + "WHERE a.id = :id AND a.version = :version")
    int softDeleteById(@Param("id") Integer id, @Param("version") Integer version,
            @Param("now") LocalDateTime now);
}
