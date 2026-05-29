package com.ibm.demo.account;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.util.SoftDeleteRepository;

public interface AccountRepository extends JpaRepository<Account, Integer>, SoftDeleteRepository<Integer> {
    List<Account> findByStatus(String status);

    @Query("SELECT a FROM Account a")
    List<Account> findAllAccount();

    @Override
    @Modifying
    @Query("""
            UPDATE Account a SET a.softDeleteMetadata.deleted = true,
            a.softDeleteMetadata.deletedAt = CURRENT_TIMESTAMP,
            a.status = 'N',
            a.version = a.version + 1
            WHERE a.id = :id AND a.version = :version
            """)
    int softDeleteById(@Param("id") Integer id, @Param("version") Integer version);
}
