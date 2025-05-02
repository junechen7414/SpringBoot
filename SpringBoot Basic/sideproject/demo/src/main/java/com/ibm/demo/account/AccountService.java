package com.ibm.demo.account;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.CreateAccountResponse;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.account.DTO.UpdateAccountResponse;
import com.ibm.demo.exception.AccountNotFoundException;
import com.ibm.demo.exception.InvalidRequestException; // 引入 InvalidRequestException
import com.ibm.demo.order.OrderClient;

import jakarta.transaction.Transactional;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final OrderClient orderClient;

    public AccountService(AccountRepository accountRepository, OrderClient orderClient) {
        this.accountRepository = accountRepository;
        this.orderClient = orderClient;
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
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));
        GetAccountDetailResponse accountDetailResponseDTO = new GetAccountDetailResponse(existingAccount.getName(),
                existingAccount.getStatus()
        // , existingAccount.getCreateDate()
        // , existingAccount.getModifiedDate()
        );
        return accountDetailResponseDTO;
    }

    @Transactional
    public UpdateAccountResponse updateAccount(UpdateAccountRequest updateAccountRequestDto) {
        // 1. 取得帳戶實體並驗證帳戶是否存在否則拋出例外
        Integer accountId = updateAccountRequestDto.getId();
        Account existingAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found with id: " + updateAccountRequestDto.getId()));
        // 2. 宣告和初始化帳戶更新前後的狀態
        String originalStatus = existingAccount.getStatus();
        String newStatus = updateAccountRequestDto.getStatus();

        // 3. 設定帳戶實體的物件
        existingAccount.setName(updateAccountRequestDto.getName());

        // 4. 驗證帳戶狀態是否更新，若有更新且要更新為N需檢核是否該帳戶仍有關聯的訂單，若仍有關聯的訂單不可更改狀態為N
        if (!originalStatus.equals(newStatus) && "N".equals(newStatus)) {
            if (orderClient.accountIdIsInOrder(accountId)) {
                throw new InvalidRequestException("Account with id: " + accountId + " has associated orders and cannot be deactivated.");
            }
            existingAccount.setStatus(newStatus);
        }

        // 5. 儲存帳戶實體
        Account updatedAccount = accountRepository.save(existingAccount);

        // 6. 準備回傳DTO
        UpdateAccountResponse updatedAccountResponseDto = new UpdateAccountResponse(updatedAccount.getId(),
                updatedAccount.getName(), updatedAccount.getStatus(), updatedAccount.getCreateDate(),
                updatedAccount.getModifiedDate());
        return updatedAccountResponseDto;
    }

    @Transactional
    public void deleteAccount(Integer accountId) {
        Account existingAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + accountId));
        if (orderClient.accountIdIsInOrder(accountId)) {
            throw new InvalidRequestException("Account with id: " + accountId + " has associated orders and cannot be deleted.");
        }
        existingAccount.setStatus("N");
        accountRepository.save(existingAccount);        
    }

    public void validateAccountExist(Integer accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException("Account not found with id: " + accountId);
        }
    }

    public void validateAccountActive(Integer accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + accountId));
        if ("N".equals(account.getStatus())) {
            throw new InvalidRequestException("Account " + accountId + " is inactive");
        }
    }
}
