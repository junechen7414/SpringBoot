package com.ibm.demo.account;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibm.demo.account.DTO.AccountDetailResponseDTO;
import com.ibm.demo.account.DTO.AccountListResponseDTO;
import com.ibm.demo.account.DTO.CreateAccountRequestDTO;
import com.ibm.demo.account.DTO.CreateAccountResponseDTO;
import com.ibm.demo.account.DTO.UpdateAccountRequestDTO;
import com.ibm.demo.account.DTO.UpdateAccountResponseDTO;

import jakarta.transaction.Transactional;

@Service
public class AccountService {
    @Autowired
    private AccountRepository accountRepository;

    @Transactional
    public CreateAccountResponseDTO createAccount(CreateAccountRequestDTO account_DTO) {
        Account newAccount = new Account(account_DTO.getName(), account_DTO.getStatus());        
        Account savedAccount = accountRepository.save(newAccount);
        CreateAccountResponseDTO createAccountResponseDTO = new CreateAccountResponseDTO(savedAccount.getId(),savedAccount.getName(), savedAccount.getStatus(),savedAccount.getCreateDate());
        return createAccountResponseDTO;
    }

    public List<AccountListResponseDTO> getAccountList() {
        return accountRepository.getAccountList();
    }

    public AccountDetailResponseDTO getAccountDetail(int id){
        Account existingAccount = accountRepository.findById(id)
            .orElseThrow(() -> new NullPointerException("Account not found with id: " + id));
        AccountDetailResponseDTO accountDetailResponseDTO = new AccountDetailResponseDTO(existingAccount.getName(), existingAccount.getStatus(),existingAccount.getCreateDate(),existingAccount.getModifiedDate());
        return accountDetailResponseDTO;    
    }

    @Transactional
    public UpdateAccountResponseDTO updateAccont(UpdateAccountRequestDTO updateAccountRequestDto){
        Account existingAccount = accountRepository.findById(updateAccountRequestDto.getId())
        .orElseThrow(() -> new NullPointerException("Account not found with id: " + updateAccountRequestDto.getId()));
        existingAccount.setName(updateAccountRequestDto.getName());
        existingAccount.setStatus(updateAccountRequestDto.getStatus());
        Account updatedAccount = accountRepository.save(existingAccount);
        UpdateAccountResponseDTO updatedAccountResponseDto = new UpdateAccountResponseDTO(updatedAccount.getId(),updatedAccount.getName(), updatedAccount.getStatus(),updatedAccount.getCreateDate(),updatedAccount.getModifiedDate());
        return updatedAccountResponseDto;
    }

    @Transactional
    public void deleteAccount(int id){
        if (accountRepository.existsById(id)) {
            accountRepository.deleteById(id);
        }else{
            throw new NullPointerException("Account not found with id: " + id);
        }
    }
}
