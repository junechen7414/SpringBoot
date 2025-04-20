package com.ibm.demo.account;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ibm.demo.account.DTO.AccountListResponseDTO;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    @Query("SELECT AccountListResponseDTO(a.id, a.name, a.status) FROM Account a")
    List<AccountListResponseDTO> getAccountList();
    
}
