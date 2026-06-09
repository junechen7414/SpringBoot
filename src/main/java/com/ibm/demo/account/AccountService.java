package com.ibm.demo.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import com.ibm.demo.util.DBAssertion;
import com.ibm.demo.util.PageResponse;
import com.ibm.demo.util.ServiceValidator;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Bulkhead(name = "AccountService")
@CircuitBreaker(name = "AccountService")
@RateLimiter(name = "AccountService")
public class AccountService {
    private final AccountRepository accountRepository;
    private final OrderClient orderClient;

    /**
     * 注入Repository和Client，已用lombok註解RequiredArgsConstructor定義建構子。
     * 
     * @param accountRepository
     * @param orderClient
     */

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
     * 獲取帳戶分頁列表。
     *
     * @param pageable 分頁參數
     * @return PageResponse<GetAccountListResponse>
     */
    @Transactional(readOnly = true)
    public PageResponse<GetAccountListResponse> getAccountList(Pageable pageable) {
        Page<GetAccountListResponse> page = accountRepository.findAllAccount(pageable)
                .map(this::mapAccountToListResponse);
        return PageResponse.from(page);
    }

    /**
     * @param id
     * @return GetAccountDetailResponse
     */
    @Transactional(readOnly = true)
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
        int updated = accountRepository.softDeleteById(accountId, existingAccount.getVersion());
        DBAssertion.assertUpdated(updated, Account.class, accountId);
    }

    // --- Private Helper Methods ---

    private GetAccountDetailResponse mapAccountToDetailResponse(Account account) {
        return GetAccountDetailResponse.builder()
                .name(account.getName())
                .status(account.getStatus())
                .build();
    }

    private GetAccountListResponse mapAccountToListResponse(Account account) {
        return new GetAccountListResponse(
                account.getId(),
                account.getName(),
                account.getStatus());
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
     * 
     * @param account   要更新的帳戶實體
     * @param newStatus 新的狀態碼
     */
    private void updateAccountStatus(Account account, String newStatus) {
        if (!account.getStatus().equals(newStatus) && AccountStatus.INACTIVE.getCode().equals(newStatus)) {
            checkAccountHasNoOrdersOrThrow(account.getId());
        }
        account.setStatus(newStatus);
    }
}
