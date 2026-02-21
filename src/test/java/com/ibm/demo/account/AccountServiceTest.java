package com.ibm.demo.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.GetAccountListResponse;
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

    // 測試資料常數
    private final Integer ACTIVE_ACCOUNT_ID = 1;
    private final String DEFAULT_NAME = "Test User";
    private final String STATUS_ACTIVE = AccountStatus.ACTIVE.getCode();
    private final String STATUS_INACTIVE = AccountStatus.INACTIVE.getCode();

    @BeforeEach
    void setUp() {
        // 手動注入 Mock 依賴，結構清晰且易於維護
        accountService = new AccountService(accountRepository, orderClient);
    }

    @Nested
    @DisplayName("建立帳戶成功流程")
    class CreateAccountSuccessTests {

        @Test
        @DisplayName("當輸入資料合法時，應成功儲存帳戶並回傳 ID")
        void createAccount_Success() {
            // Arrange
            CreateAccountRequest request = new CreateAccountRequest(DEFAULT_NAME);
            
            Account savedAccount = new Account();
            savedAccount.setId(100);
            when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

            // Act
            Integer resultId = accountService.createAccount(request);

            // Assert
            assertThat(resultId).isEqualTo(100);

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            assertThat(captor.getValue())
                    .hasFieldOrPropertyWithValue("name", DEFAULT_NAME)
                    .hasFieldOrPropertyWithValue("status", STATUS_ACTIVE);
        }
    }

    @Nested
    @DisplayName("查詢帳戶成功流程")
    class GetAccountSuccessTests {

        @Test
        @DisplayName("查詢所有帳戶應回傳列表")
        void getAccountList_Success() {
            // Arrange
            List<GetAccountListResponse> expectedList = List.of(
                new GetAccountListResponse(1, "User1", STATUS_ACTIVE),
                new GetAccountListResponse(2, "User2", STATUS_INACTIVE)
            );
            when(accountRepository.findAllAccount()).thenReturn(expectedList);

            // Act
            List<GetAccountListResponse> actualList = accountService.getAccountList();

            // Assert
            assertThat(actualList).hasSize(2).isEqualTo(expectedList);
            verify(accountRepository).findAllAccount();
        }

        @Test
        @DisplayName("查詢存在的帳戶應成功回傳詳細資訊")
        void getAccountDetail_WhenExists_Success() {
            // Arrange
            Account existingAccount = createTestAccount(ACTIVE_ACCOUNT_ID, DEFAULT_NAME, STATUS_ACTIVE);
            when(accountRepository.findById(ACTIVE_ACCOUNT_ID)).thenReturn(Optional.of(existingAccount));

            // Act
            var response = accountService.getAccountDetail(ACTIVE_ACCOUNT_ID);

            // Assert
            assertThat(response)
                    .hasFieldOrPropertyWithValue("name", DEFAULT_NAME)
                    .hasFieldOrPropertyWithValue("status", STATUS_ACTIVE);

            verify(accountRepository).findById(ACTIVE_ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("查詢帳戶業務邏輯")
    class GetAccountTests {

        @ParameterizedTest
        @ValueSource(ints = {999, 888})
        @DisplayName("查詢時若 ID 不存在，應拋出 ResourceNotFoundException")
        void getAccountDetail_WhenNotFound_ShouldThrowException(Integer nonExistentId) {
            when(accountRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountDetail(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found")
                    .hasMessageContaining(String.valueOf(nonExistentId));
        }
    }

    @Nested
    @DisplayName("更新帳戶成功流程")
    class UpdateAccountSuccessTests {

        @Test
        @DisplayName("將帳戶狀態從 N 更新為 Y 時，應直接儲存而不檢查訂單")
        void updateAccount_StatusFromNToY_Success() {
            // Arrange
            Account inactiveAccount = createTestAccount(ACTIVE_ACCOUNT_ID, DEFAULT_NAME, STATUS_INACTIVE);
            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .id(ACTIVE_ACCOUNT_ID)
                    .name("Updated Name")
                    .status(STATUS_ACTIVE)
                    .build();

            when(accountRepository.findById(ACTIVE_ACCOUNT_ID)).thenReturn(Optional.of(inactiveAccount));

            // Act
            accountService.updateAccount(request);

            // Assert
            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            assertThat(captor.getValue())
                    .hasFieldOrPropertyWithValue("name", "Updated Name")
                    .hasFieldOrPropertyWithValue("status", STATUS_ACTIVE);
            
            // 重要驗證：確保沒有呼叫 orderClient（因為是啟用帳戶）
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("更新帳戶名稱但保持狀態不變，應成功儲存")
        void updateAccount_SameStatus_Success() {
            // Arrange
            Account activeAccount = createTestAccount(ACTIVE_ACCOUNT_ID, DEFAULT_NAME, STATUS_ACTIVE);
            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .id(ACTIVE_ACCOUNT_ID)
                    .name("New Name")
                    .status(STATUS_ACTIVE)
                    .build();

            when(accountRepository.findById(ACTIVE_ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));

            // Act
            accountService.updateAccount(request);

            // Assert
            verify(accountRepository).save(any(Account.class));
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
                    .id(id).name("Any Name").status(STATUS_ACTIVE).build();

            when(accountRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.updateAccount(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found")
                    .hasMessageContaining(String.valueOf(id));

            verify(accountRepository, never()).save(any());
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("帳戶改為停效(N)時，若仍有關聯訂單應拋出異常")
        void updateAccount_WhenStatusChangeToInactiveAndHasOrder_ShouldThrowException() {
            // Arrange
            Account activeAccount = createTestAccount(ACTIVE_ACCOUNT_ID, DEFAULT_NAME, STATUS_ACTIVE);

            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .id(ACTIVE_ACCOUNT_ID)
                    .status(STATUS_INACTIVE)
                    .build();

            when(accountRepository.findById(ACTIVE_ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));
            when(orderClient.accountIdIsInOrder(ACTIVE_ACCOUNT_ID)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> accountService.updateAccount(request))
                    .isInstanceOf(AccountStillHasOrderCanNotBeDeleteException.class)
                    .hasMessageContaining("associated orders");

            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("刪除帳戶成功流程")
    class DeleteAccountSuccessTests {

        @Test
        @DisplayName("刪除存在的活躍帳戶且無訂單時應成功")
        void deleteAccount_Success() {
            // Arrange
            Account activeAccount = createTestAccount(ACTIVE_ACCOUNT_ID, DEFAULT_NAME, STATUS_ACTIVE);
            when(accountRepository.findById(ACTIVE_ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));
            when(orderClient.accountIdIsInOrder(ACTIVE_ACCOUNT_ID)).thenReturn(false);

            // Act
            accountService.deleteAccount(ACTIVE_ACCOUNT_ID);

            // Assert
            verify(accountRepository).delete(activeAccount);
        }
    }

    @Nested
    @DisplayName("刪除帳戶業務邏輯")
    class DeleteAccountTests {

        @ParameterizedTest
        @ValueSource(ints = {888, 777})
        @DisplayName("刪除時若 ID 不存在，應拋出 ResourceNotFoundException")
        void deleteAccount_WhenNotFound_ShouldThrowException(Integer id) {
            when(accountRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.deleteAccount(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found")
                    .hasMessageContaining(String.valueOf(id));

            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("刪除時若帳戶已是停效狀態，應拋出 ResourceNotFoundException")
        void deleteAccount_WhenAccountAlreadyInactive_shouldThrowResourceNotFoundException() {
            // Arrange
            Integer id = 2;
            // 模擬 Repository 回傳 Optional.empty() (因 @SQLRestriction)
            when(accountRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> accountService.deleteAccount(id))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(orderClient, never()).accountIdIsInOrder(any());
        }

        @Test
        @DisplayName("刪除時若仍有關聯訂單，應拋出異常")
        void deleteAccount_WhenHasOrder_ShouldThrowException() {
            Account activeAccount = createTestAccount(ACTIVE_ACCOUNT_ID, DEFAULT_NAME, STATUS_ACTIVE);

            when(accountRepository.findById(ACTIVE_ACCOUNT_ID)).thenReturn(Optional.of(activeAccount));
            when(orderClient.accountIdIsInOrder(ACTIVE_ACCOUNT_ID)).thenReturn(true);

            assertThatThrownBy(() -> accountService.deleteAccount(ACTIVE_ACCOUNT_ID))
                    .isInstanceOf(AccountStillHasOrderCanNotBeDeleteException.class)
                    .hasMessageContaining("associated orders");

            verify(accountRepository, never()).delete(any());
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
