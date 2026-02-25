package com.ibm.demo.account;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.exception.BusinessLogicCheck.AccountStillHasOrderCanNotBeDeleteException;
import com.ibm.demo.exception.BusinessLogicCheck.ResourceNotFoundException;
import com.ibm.demo.order.OrderClient;
import com.ibm.demo.util.ServiceValidator;

import jakarta.transaction.Transactional;
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
    public void updateAccount(UpdateAccountRequest updateAccountRequestDto) {
        ServiceValidator.validateNotNull(updateAccountRequestDto, "Update account request");
        // 1. 取得帳戶實體並驗證帳戶是否存在否則拋出例外
        Integer accountId = updateAccountRequestDto.id();
        Account existingAccount = findAccountByIdOrThrow(accountId);
        // 2. 宣告和初始化帳戶更新前後的狀態
        String originalStatus = existingAccount.getStatus();
        String newStatus = updateAccountRequestDto.status();

        // 3. 設定帳戶實體的物件
        existingAccount.setName(updateAccountRequestDto.name());

        // 4. 驗證帳戶狀態是否更新，若有更新且要更新為N需檢核是否該帳戶仍有關聯的訂單，若仍有關聯的訂單不可更改狀態為N
        if (!originalStatus.equals(newStatus) && AccountStatus.INACTIVE.getCode().equals(newStatus)) {
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
        checkAccountHasNoOrdersOrThrow(accountId);
        // 使用 delete() 觸發 @SQLDelete，執行軟刪除邏輯
        accountRepository.delete(existingAccount);
    }

    // --- Private Helper Methods ---

    private GetAccountDetailResponse mapAccountToDetailResponse(Account account) {
        return new GetAccountDetailResponse(account.getName(), account.getStatus());
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
}
