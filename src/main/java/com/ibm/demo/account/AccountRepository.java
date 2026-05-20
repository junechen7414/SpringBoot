package com.ibm.demo.account;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.util.SoftDeleteRepository;

public interface AccountRepository extends JpaRepository<Account, Integer>, SoftDeleteRepository<Integer> {
    @Query("SELECT new com.ibm.demo.account.DTO.GetAccountListResponse(a.id, a.name, a.status) FROM Account a WHERE a.status = :status")
    List<GetAccountListResponse> findByStatus(String status);

    @Query("SELECT new com.ibm.demo.account.DTO.GetAccountListResponse(a.id, a.name, a.status) FROM Account a")
    List<GetAccountListResponse> findAllAccount();

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
