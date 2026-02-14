package com.ibm.demo.account;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.AccountStillHasOrderCanNotBeDeleteException;
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

    /**
     * @param account_DTO
     * @return CreateAccountResponse
     */
    @Transactional
    public Integer createAccount(CreateAccountRequest account_DTO) {
        Account newAccount = new Account();
        newAccount.setName(account_DTO.name());
        // 預設帳戶狀態為Y，啟用
        newAccount.setStatus("Y");

        Account savedAccount = accountRepository.save(newAccount);
        return savedAccount.getId();
    }

    /**
     * @return List<GetAccountListResponse>
     */
    public List<GetAccountListResponse> getAccountList(String status) {
        if (status == null || status.isEmpty()) {
            return accountRepository.findAllAccount();
        }
        return accountRepository.findByStatus(status);
    }

    /**
     * @param id
     * @return GetAccountDetailResponse
     */
    public GetAccountDetailResponse getAccountDetail(Integer id) {
        Account existingAccount = findAccountByIdOrThrow(id);
        GetAccountDetailResponse accountDetailResponseDTO = new GetAccountDetailResponse(existingAccount.getName(),
                existingAccount.getStatus());
        return accountDetailResponseDTO;
    }

    /**
     * @param updateAccountRequestDto
     */
    @Transactional
    public void updateAccount(UpdateAccountRequest updateAccountRequestDto) {
        // 1. 取得帳戶實體並驗證帳戶是否存在否則拋出例外
        Integer accountId = updateAccountRequestDto.id();
        Account existingAccount = findAccountByIdOrThrow(accountId);
        // 2. 宣告和初始化帳戶更新前後的狀態
        String originalStatus = existingAccount.getStatus();
        String newStatus = updateAccountRequestDto.status();

        // 3. 設定帳戶實體的物件
        existingAccount.setName(updateAccountRequestDto.name());

        // 4. 驗證帳戶狀態是否更新，若有更新且要更新為N需檢核是否該帳戶仍有關聯的訂單，若仍有關聯的訂單不可更改狀態為N
        if (!originalStatus.equals(newStatus) && "N".equals(newStatus)) {
            checkAccountHasNoOrdersOrThrow(accountId);
        }
        existingAccount.setStatus(newStatus);

        // 5. 儲存帳戶實體
        accountRepository.save(existingAccount);

    }

    /**
     * @param accountId
     */
    @Transactional
    public void deleteAccount(Integer accountId) {
        Account existingAccount = findAccountByIdOrThrow(accountId);
        if (existingAccount.getStatus().equals("N")) {
            throw new ResourceNotFoundException("Account not found with ID: " + accountId);
        }
        checkAccountHasNoOrdersOrThrow(accountId);
        existingAccount.setStatus("N");
        accountRepository.save(existingAccount);
    }

    // --- Private Helper Methods ---

    // validateAccount
    // @Transactional
    // public void validateAccount(Integer accountId) {
    // Account existingAccount = findAccountByIdOrThrow(accountId);
    // if (existingAccount.getStatus().equals("N")){
    // throw new AccountInactiveException("Account is inactive with id: " +
    // accountId);
    // }
    // }

    /**
     * Finds an account by its ID or throws AccountNotFoundException if not found.
     */
    private Account findAccountByIdOrThrow(Integer accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
    }

    /**
     * Checks if an account has associated orders via OrderClient. Throws
     * AccountStillHasOrderCanNotBeDeleteException if orders exist.
     */
    private void checkAccountHasNoOrdersOrThrow(Integer accountId) {
        if (orderClient.accountIdIsInOrder(accountId)) {
            throw new AccountStillHasOrderCanNotBeDeleteException(
                    "Account with id: " + accountId + " has associated orders and cannot be set to deactivate.");
        }
    }
}
