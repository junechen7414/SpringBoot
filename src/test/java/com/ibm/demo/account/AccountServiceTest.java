package com.ibm.demo.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.AccountStillHasOrderCanNotBeDeleteException;
import com.ibm.demo.order.OrderClient;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OrderClient orderClient;

    // 顯性建立被測物件 (SUT)
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        // 手動注入 Mock 依賴，結構清晰且易於維護
        accountService = new AccountService(accountRepository, orderClient);
    }

    @Nested
    @DisplayName("查詢帳戶業務邏輯")
    class GetAccountTests {

        @Test
        @DisplayName("查詢時若 ID 不存在，應拋出 ResourceNotFoundException")
        void getAccountDetail_WhenNotFound_ShouldThrowException() {
            Integer nonExistentId = 999;
            when(accountRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountDetail(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("更新帳戶成功流程")
    class UpdateAccountSuccessTests {

        @Test
        @DisplayName("將帳戶狀態從 N 更新為 Y 時，應直接儲存而不檢查訂單")
        void updateAccount_StatusFromNToY_Success() {
            // Arrange
            Integer accountId = 1;
            Account inactiveAccount = createTestAccount(accountId, "User", AccountStatus.INACTIVE.getCode());
            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .id(accountId).status(AccountStatus.ACTIVE.getCode()).build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(inactiveAccount));

            // Act
            accountService.updateAccount(request);

            // Assert
            verify(accountRepository).save(any(Account.class));
            // 重要驗證：確保沒有呼叫 orderClient（因為是啟用帳戶）
            verifyNoInteractions(orderClient);
        }
    }

    @Nested
    @DisplayName("更新帳戶業務邏輯")
    class UpdateAccountTests {

        @Test
        @DisplayName("更新時若帳戶不存在，應拋出 ResourceNotFoundException")
        void updateAccount_WhenNotFound_ShouldThrowException() {
            Integer id = 999;
            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .id(id).name("Any Name").status(AccountStatus.ACTIVE.getCode()).build();

            when(accountRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.updateAccount(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(accountRepository, never()).save(any());
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("帳戶改為停效(N)時，若仍有關聯訂單應拋出異常")
        void updateAccount_WhenStatusChangeToInactiveAndHasOrder_ShouldThrowException() {
            // Arrange: 建立獨立測試物件，確保隔離性
            Integer accountId = 1;
            Account activeAccount = createTestAccount(accountId, "Active User", AccountStatus.ACTIVE.getCode());

            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .id(accountId)
                    .status(AccountStatus.INACTIVE.getCode())
                    .build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
            when(orderClient.accountIdIsInOrder(accountId)).thenReturn(true);

            // Act & Assert: 使用 AssertJ 斷言
            assertThatThrownBy(() -> accountService.updateAccount(request))
                    .isInstanceOf(AccountStillHasOrderCanNotBeDeleteException.class);

            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("刪除帳戶業務邏輯")
    class DeleteAccountTests {

        @Test
        @DisplayName("刪除時若 ID 不存在，應拋出 ResourceNotFoundException")
        void deleteAccount_WhenNotFound_ShouldThrowException() {
            Integer id = 888;
            when(accountRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.deleteAccount(id))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("刪除時若帳戶已是停效狀態，應拋出 ResourceNotFoundException")
        void deleteAccount_WhenAccountAlreadyInactive_shouldThrowResourceNotFoundException() {
            // Arrange
            Integer id = 2;
            // 在真實情況下，@SQLRestriction 會導致 findById 查不到 STATUS='N' 的資料
            // 因此模擬 Repository 回傳 Optional.empty()
            when(accountRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> {
                accountService.deleteAccount(id);
            }).isInstanceOf(ResourceNotFoundException.class);

            // 驗證流程在第一步就斷了，沒有去查訂單
            verify(orderClient, never()).accountIdIsInOrder(any());
        }

        @Test
        @DisplayName("刪除時若仍有關聯訂單，應拋出異常")
        void deleteAccount_WhenHasOrder_ShouldThrowException() {
            Integer id = 1;
            Account activeAccount = createTestAccount(id, "Active User", AccountStatus.ACTIVE.getCode());

            when(accountRepository.findById(id)).thenReturn(Optional.of(activeAccount));
            when(orderClient.accountIdIsInOrder(id)).thenReturn(true);

            assertThatThrownBy(() -> accountService.deleteAccount(id))
                    .isInstanceOf(AccountStillHasOrderCanNotBeDeleteException.class);

            verify(accountRepository, never()).save(any());
        }
    }

    // --- Helper Methods ---
    private Account createTestAccount(Integer id, String name, String status) {
        Account account = new Account();
        account.setId(id);
        account.setName(name);
        account.setStatus(status);
        return account;
    }
}