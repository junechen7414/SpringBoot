package com.ibm.demo.account;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ibm.demo.account.DTO.GetAccountListResponse;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    @Query("SELECT new com.ibm.demo.account.DTO.GetAccountListResponse(a.id, a.name, a.status) FROM Account a WHERE a.status != 'N'")
    List<GetAccountListResponse> getAccountList();


}
