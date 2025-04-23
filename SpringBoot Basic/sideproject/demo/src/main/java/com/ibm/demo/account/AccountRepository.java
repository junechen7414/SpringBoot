package com.ibm.demo.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ibm.demo.account.DTO.GetAccountListResponse;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    @Query("SELECT AccountListResponseDTO(a.id, a.name, a.status) FROM Account a")
    List<GetAccountListResponse> getAccountList();

    @EntityGraph(attributePaths = { "orders" })
    Optional<Account> findByIdWithOrders(int id);

}
