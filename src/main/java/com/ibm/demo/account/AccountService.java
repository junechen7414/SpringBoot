package com.ibm.demo.account;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.exception.BusinessLogicCheck.AccountStillHasOrderCanNotBeDeleteException;
import com.ibm.demo.exception.BusinessLogicCheck.ResourceNotFoundException;
import com.ibm.demo.order.OrderClient;
import com.ibm.demo.util.ServiceValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final OrderClient orderClient;

    /**
     * 注入Repository和Client，已用lombok註解RequiredArgsConstructor定義建構子。
     * @param accountRepository
     * @param orderClient
     */
    // public AccountService(AccountRepository accountRepository, OrderClient orderClient) {
    //     this.accountRepository = accountRepository;
    //     this.orderClient = orderClient;
    // }

    /**
     * @param account_DTO
     * @return CreateAccountResponse
     */
    @Transactional
    public Integer createAccount(CreateAccountRequest account_DTO) {
        ServiceValidator.validateNotNull(account_DTO, "Create account request");

        Account newAccount = Account.builder()
                .name(account_DTO.name())
                .status(AccountStatus.ACTIVE.getCode())
                .build();

        Account savedAccount = accountRepository.save(newAccount);
        return savedAccount.getId();
    }

    /**
     * @return List<GetAccountListResponse>
     */
    public List<GetAccountListResponse> getAccountList() {
        return accountRepository.findAllAccount();
    }

    /**
     * @param id
     * @return GetAccountDetailResponse
     */
    public GetAccountDetailResponse getAccountDetail(Integer id) {
        Account existingAccount = findAccountByIdOrThrow(id);
        return mapAccountToDetailResponse(existingAccount);
    }

    /**
     * @param updateAccountRequestDto
     */
    @Transactional
    public void updateAccount(Integer id, UpdateAccountRequest updateAccountRequestDto) {
        ServiceValidator.validateNotNull(updateAccountRequestDto, "Update account request");
        // 1. 取得帳戶實體並驗證帳戶是否存在否則拋出例外
        Account existingAccount = findAccountByIdOrThrow(id);

        // 2. 更新帳戶名稱
        existingAccount.setName(updateAccountRequestDto.name());

        // 3. 更新帳戶狀態 (包含業務邏輯檢查)
        updateAccountStatus(existingAccount, updateAccountRequestDto.status());

        // 5. 儲存帳戶實體
        accountRepository.save(existingAccount);

    }

    /**
     * @param accountId
     */
    @Transactional
    public void deleteAccount(Integer accountId) {
        Account existingAccount = findAccountByIdOrThrow(accountId);
        checkAccountHasNoOrdersOrThrow(accountId);
        // 使用 delete() 觸發 @SQLDelete，執行軟刪除邏輯
        accountRepository.delete(existingAccount);
    }

    // --- Private Helper Methods ---

    private GetAccountDetailResponse mapAccountToDetailResponse(Account account) {
        return GetAccountDetailResponse.builder()
                .name(account.getName())
                .status(account.getStatus())
                .build();
    }

    /**
     * Finds an account by its ID or throws AccountNotFoundException if not found.
     */
    private Account findAccountByIdOrThrow(Integer accountId) {
        ServiceValidator.validateNotNull(accountId, "Account ID");
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
    }

    /**
     * Checks if an account has associated orders via OrderClient. Throws
     * AccountStillHasOrderCanNotBeDeleteException if orders exist.
     */
    private void checkAccountHasNoOrdersOrThrow(Integer accountId) {
        ServiceValidator.validateNotNull(accountId, "Account ID");
        if (orderClient.accountIdIsInOrder(accountId)) {
            throw new AccountStillHasOrderCanNotBeDeleteException(
                    "Account with id: " + accountId + " has associated orders and cannot be set to deactivate.");
        }
    }

    /**
     * 更新帳戶狀態，並在需要時執行業務邏輯檢查。
     * 如果狀態從啟用變為停用，會檢查帳戶是否仍有關聯訂單。
     * @param account 要更新的帳戶實體
     * @param newStatus 新的狀態碼
     */
    private void updateAccountStatus(Account account, String newStatus) {
        if (!account.getStatus().equals(newStatus) && AccountStatus.INACTIVE.getCode().equals(newStatus)) {
            checkAccountHasNoOrdersOrThrow(account.getId());
        }
        account.setStatus(newStatus);
    }
}
