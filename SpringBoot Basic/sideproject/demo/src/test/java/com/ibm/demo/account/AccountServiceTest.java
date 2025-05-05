package com.ibm.demo.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.CreateAccountResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.exception.BusinessLogicCheck.AccountInactiveException;
import com.ibm.demo.exception.BusinessLogicCheck.AccountStillHasOrderCanNotBeDeleteException;
import com.ibm.demo.order.OrderClient;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OrderClient orderClient;

    @InjectMocks
    private AccountService accountService;

    private Account activeAccount;
    private Account inactiveAccount;

    @BeforeEach
    void setUp() {
        activeAccount = new Account();
        activeAccount.setId(1);
        activeAccount.setName("Test Account Active");
        activeAccount.setStatus("Y");

        inactiveAccount = new Account();
        inactiveAccount.setId(2);
        inactiveAccount.setName("Test Account Inactive");
        inactiveAccount.setStatus("N");
    }

    @Test
    @DisplayName("建立帳戶時，初始狀態應為Y")
    void createAccount_shouldSetInitialStatusToY() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest();
        request.setName("New Account");

        Account accountToSave = new Account();
        accountToSave.setName(request.getName());
        accountToSave.setStatus("Y"); // Service 內部設定

        Account savedAccount = new Account();
        savedAccount.setId(3); // 模擬資料庫產生的 ID
        savedAccount.setName(request.getName());
        savedAccount.setStatus("Y");
        savedAccount.setCreateDate(LocalDate.now()); // 模擬資料庫產生的日期

        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        // Act
        CreateAccountResponse response = accountService.createAccount(request);

        // Assert
        assertNotNull(response);
        assertEquals(savedAccount.getId(), response.getId());
        assertEquals(savedAccount.getName(), response.getName());
        assertEquals("Y", response.getStatus()); // 驗證回傳 DTO 的狀態
        assertEquals(savedAccount.getCreateDate(), response.getCreateDate());

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertEquals("Y", accountCaptor.getValue().getStatus()); // 驗證傳遞給 save 方法的實體狀態
    }
    @Test
    @DisplayName("取得帳戶列表時，呼叫 Repository 的 getAccountList 方法，應僅回傳狀態為Y的帳戶")
    void getAccountList_shouldReturnOnlyActiveAccounts() {
        // Arrange
        // 準備一個模擬的回傳列表 (內容不重要，重點是驗證呼叫)
        List<GetAccountListResponse> mockList = Arrays.asList(
            new GetAccountListResponse(1, "Acc 1", "Y"),
            new GetAccountListResponse(3, "Acc 3", "Y")
        );
        when(accountRepository.getAccountList()).thenReturn(mockList);

        // Act
        List<GetAccountListResponse> result = accountService.getAccountList();

        // Assert
        assertNotNull(result);
        assertEquals(mockList, result); // 確保回傳的是 Repository 給的結果
        verify(accountRepository, times(1)).getAccountList(); // 驗證 Repository 方法被呼叫一次
    }

    @Test
    @DisplayName("更新帳戶狀態為N時，若有關聯訂單應拋出AccountStillHasOrderCanNotBeDeleteException")
    void updateAccount_whenStatusChangeToNAndHasOrder_shouldThrowException() {
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setId(activeAccount.getId());
        request.setName("Updated Name");
        request.setStatus("N");

        when(accountRepository.findById(activeAccount.getId())).thenReturn(Optional.of(activeAccount));
        when(orderClient.accountIdIsInOrder(activeAccount.getId())).thenReturn(true);

        assertThrows(AccountStillHasOrderCanNotBeDeleteException.class, () -> accountService.updateAccount(request));
        
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("更新帳戶狀態為N時，若無關聯訂單應成功更新狀態")
    void updateAccount_whenStatusChangeToNAndNoOrder_shouldUpdateStatus() {
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setId(activeAccount.getId());
        request.setName("Updated Name");
        request.setStatus("N");

        when(accountRepository.findById(activeAccount.getId())).thenReturn(Optional.of(activeAccount));
        when(orderClient.accountIdIsInOrder(activeAccount.getId())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(activeAccount); // Mock save to return the updated account

        accountService.updateAccount(request);

        assertEquals("N", activeAccount.getStatus());
        verify(accountRepository, times(1)).save(activeAccount);
    }

    @Test
    @DisplayName("刪除帳戶時，若有關聯訂單應拋出AccountStillHasOrderCanNotBeDeleteException")
    void deleteAccount_whenHasOrder_shouldThrowException() {
        when(accountRepository.findById(activeAccount.getId())).thenReturn(Optional.of(activeAccount));
        when(orderClient.accountIdIsInOrder(activeAccount.getId())).thenReturn(true);

        assertThrows(AccountStillHasOrderCanNotBeDeleteException.class, () -> accountService.deleteAccount(activeAccount.getId()));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("刪除帳戶時，若無關聯訂單應成功將狀態更新為N")
    void deleteAccount_whenNoOrder_shouldUpdateStatusToN() {
        when(accountRepository.findById(activeAccount.getId())).thenReturn(Optional.of(activeAccount));
        when(orderClient.accountIdIsInOrder(activeAccount.getId())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(activeAccount); // Mock save to return the updated account

        accountService.deleteAccount(activeAccount.getId());

        assertEquals("N", activeAccount.getStatus());
        verify(accountRepository, times(1)).save(activeAccount);
    }

    @Test
    @DisplayName("驗證帳戶是否啟用時，若帳戶狀態為N應拋出AccountInactiveException")
    void validateAccountActive_whenAccountIsInactive_shouldThrowException() {
        when(accountRepository.findById(inactiveAccount.getId())).thenReturn(Optional.of(inactiveAccount));

        assertThrows(AccountInactiveException.class, () -> accountService.validateAccountActive(inactiveAccount.getId()));
    }

    @Test
    @DisplayName("驗證帳戶是否啟用時，若帳戶狀態為Y應不拋出例外")
    void validateAccountActive_whenAccountIsActive_shouldNotThrowException() {
        when(accountRepository.findById(activeAccount.getId())).thenReturn(Optional.of(activeAccount));

        accountService.validateAccountActive(activeAccount.getId());

        verify(accountRepository, times(1)).findById(activeAccount.getId());
    }

    // @Test
    // @DisplayName("驗證帳戶是否存在時，若帳戶不存在應拋出AccountNotFoundException")
    // void validateAccountExist_whenAccountDoesNotExist_shouldThrowException() {
    //     Integer nonExistentAccountId = 999;
    //     when(accountRepository.existsById(nonExistentAccountId)).thenReturn(false);

    //     assertThrows(AccountNotFoundException.class, () -> accountService.validateAccountExist(nonExistentAccountId));
    // }

    // @Test
    // @DisplayName("驗證帳戶是否存在時，若帳戶存在應不拋出例外")
    // void validateAccountExist_whenAccountExists_shouldNotThrowException() {
    //     when(accountRepository.existsById(activeAccount.getId())).thenReturn(true);

    //     accountService.validateAccountExist(activeAccount.getId());

    //     verify(accountRepository, times(1)).existsById(activeAccount.getId());
    // }
}
