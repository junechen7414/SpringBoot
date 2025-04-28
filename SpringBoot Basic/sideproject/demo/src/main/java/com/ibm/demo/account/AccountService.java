package com.ibm.demo.account;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.CreateAccountResponse;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.account.DTO.UpdateAccountResponse;

import jakarta.transaction.Transactional;

@Service
public class AccountService {    
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public CreateAccountResponse createAccount(CreateAccountRequest account_DTO) {        
        Account newAccount = new Account();
        newAccount.setName(account_DTO.getName());
        newAccount.setStatus("Y");
        
        Account savedAccount = accountRepository.save(newAccount);
        CreateAccountResponse createAccountResponseDTO = new CreateAccountResponse(savedAccount.getId(),
                savedAccount.getName(), savedAccount.getStatus(), savedAccount.getCreateDate());
        return createAccountResponseDTO;
    }

    public List<GetAccountListResponse> getAccountList() {
        return accountRepository.getAccountList();
    }

    public GetAccountDetailResponse getAccountDetail(Integer id) {
        Account existingAccount = accountRepository.findById(id)
                .orElseThrow(() -> new NullPointerException("Account not found with id: " + id));
        GetAccountDetailResponse accountDetailResponseDTO = new GetAccountDetailResponse(existingAccount.getName(),
                existingAccount.getStatus(), existingAccount.getCreateDate()
                // , existingAccount.getModifiedDate()
                );
        return accountDetailResponseDTO;
    }

    @Transactional
    public UpdateAccountResponse updateAccount(UpdateAccountRequest updateAccountRequestDto) {
        Account existingAccount = accountRepository.findById(updateAccountRequestDto.getId())
                .orElseThrow(() -> new NullPointerException(
                        "Account not found with id: " + updateAccountRequestDto.getId()));
        existingAccount.setName(updateAccountRequestDto.getName());
        existingAccount.setStatus(updateAccountRequestDto.getStatus());
        Account updatedAccount = accountRepository.save(existingAccount);
        UpdateAccountResponse updatedAccountResponseDto = new UpdateAccountResponse(updatedAccount.getId(),
                updatedAccount.getName(), updatedAccount.getStatus(), updatedAccount.getCreateDate(),
                updatedAccount.getModifiedDate());
        return updatedAccountResponseDto;
    }

    @Transactional
    public void deleteAccount(Integer id) {
        Account existingAccount = accountRepository.findById(id)
                .orElseThrow(() -> new NullPointerException("Account not found with id: " + id));
        existingAccount.setStatus("N");
        accountRepository.save(existingAccount);
    }

    public void validateActiveAccount(Integer accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow(()-> new NullPointerException("Account not found with id: " + accountId));
        if ("N".equals(account.getStatus())) {
            throw new IllegalArgumentException("Account " + accountId + " is inactive");
        }
    }
}
