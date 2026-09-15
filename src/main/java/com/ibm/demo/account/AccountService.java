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
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.util.DBAssertion;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.util.PageResponse;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@CircuitBreaker(name = "AccountService")
public class AccountService {
    private final AccountRepository accountRepository;

    /**
     * @param account_DTO
     * @return CreateAccountResponse
     */
    @Transactional
    @Bulkhead(name = "account-write")
    @RateLimiter(name = "account-write")
    public Integer createAccount(CreateAccountRequest account_DTO) {

        Account newAccount = Account.builder()
                .name(account_DTO.name())
                .status(AccountStatus.ACTIVE.getCode())
                .build();

        Account savedAccount = accountRepository.save(newAccount);
        log.info("帳戶建立成功，帳戶ID: {}, 名稱: {}", savedAccount.getId(), savedAccount.getName());
        return savedAccount.getId();
    }

    /**
     * 獲取帳戶分頁列表。
     *
     * @param pageable 分頁參數
     * @return PageResponse<GetAccountListResponse>
     */
    @Transactional(readOnly = true)
    @Bulkhead(name = "account-read")
    @RateLimiter(name = "account-read")    
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
    @Bulkhead(name = "account-read")
    @RateLimiter(name = "account-read")
    public GetAccountDetailResponse getAccountDetail(Integer id) {
        Account existingAccount = findAccountByIdOrThrow(id);
        return mapAccountToDetailResponse(existingAccount);
    }

    /**
     * 驗證帳戶是否具下單資格。下單資格規則收斂於帳戶領域，呼叫端（如訂單服務）
     * 只需表達意圖，無須知道帳戶的狀態欄位或代碼。
     * <p>
     * 帳戶實體受 {@code @SQLRestriction("STATUS = 'Y' AND DELETED = false")} 限制，
     * 停用或已軟刪除的帳戶查詢即不可見，故「存在且可載入」等同於「具下單資格」；
     * 不符者一律由 {@code findAccountByIdOrThrow} 拋出 BusinessException（RESOURCE_NOT_FOUND, 404）。
     *
     * @param id 帳戶 ID
     */
    @Transactional(readOnly = true)
    @Bulkhead(name = "account-read")
    @RateLimiter(name = "account-read")
    public void assertCanPlaceOrder(Integer id) {
        findAccountByIdOrThrow(id);
    }

    /**
     * 更新帳戶名稱與狀態。
     *
     * <p><b>不含</b>「停用前須無有效訂單」的檢查 —— 那條不變量橫跨 order 領域，已上提至
     * {@code orchestration.AccountLifecycleService}（見該處說明：account 不該反向依賴 order）。
     * 外部流量一律經由該層進入，直接呼叫本方法不會有訂單檢查。
     *
     * @param updateAccountRequestDto
     */
    @Transactional
    @Bulkhead(name = "account-write-with-validation")
    @RateLimiter(name = "account-write-with-validation")
    public void updateAccount(Integer id, UpdateAccountRequest updateAccountRequestDto) {
        // 1. 取得帳戶實體並驗證帳戶是否存在否則拋出例外
        Account existingAccount = findAccountByIdOrThrow(id);

        // 2. 更新帳戶名稱
        existingAccount.setName(updateAccountRequestDto.name());

        // 3. 更新帳戶狀態
        existingAccount.setStatus(updateAccountRequestDto.status().getCode());

        // 4. 儲存帳戶實體
        accountRepository.save(existingAccount);
        log.info("帳戶更新成功，帳戶ID: {}, 狀態: {}", id, existingAccount.getStatus());
    }

    /**
     * 軟刪除帳戶。
     *
     * <p>同 {@link #updateAccount}：<b>不含</b>「須無有效訂單」的檢查，該守門在
     * {@code orchestration.AccountLifecycleService}。
     *
     * @param accountId
     */
    @Transactional
    @Bulkhead(name = "account-write-with-validation")
    @RateLimiter(name = "account-write-with-validation")
    public void deleteAccount(Integer accountId) {
        Account existingAccount = findAccountByIdOrThrow(accountId);
        int updated = accountRepository.softDeleteById(accountId, existingAccount.getVersion());
        DBAssertion.assertUpdated(updated, Account.class, accountId);
        log.info("帳戶軟刪除成功，帳戶ID: {}", accountId);
    }

    // --- Private Helper Methods ---

    private GetAccountDetailResponse mapAccountToDetailResponse(Account account) {
        return GetAccountDetailResponse.builder()
                .name(account.getName())
                .status(AccountStatus.fromCode(account.getStatus()))
                .build();
    }

    private GetAccountListResponse mapAccountToListResponse(Account account) {
        return new GetAccountListResponse(
                account.getId(),
                account.getName(),
                AccountStatus.fromCode(account.getStatus()));
    }

    /**
     * Finds an account by its ID or throws AccountNotFoundException if not found.
     */
    private Account findAccountByIdOrThrow(Integer accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Account not found with id: " + accountId));
    }
}
