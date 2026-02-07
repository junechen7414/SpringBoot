package com.ibm.demo.account;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.exception.ResourceNotFoundException;
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

    private static Account activeAccount;
    private static Account inactiveAccount;

    @BeforeAll
    static void setUp() {
        activeAccount = new Account();
        activeAccount.setId(1);
        activeAccount.setName("Test Account Active");
        activeAccount.setStatus("Y");

        inactiveAccount = new Account();
        inactiveAccount.setId(2);
        inactiveAccount.setName("Test Account Inactive");
        inactiveAccount.setStatus("N");
    }

    // @Test
    // @DisplayName("獲取帳戶詳情時，若帳戶不存在應拋出ResourceNotFoundException")
    // void
    // getAccountDetail_whenAccountNotFound_shouldThrowResourceNotFoundException() {
    // // Arrange
    // Integer nonExistentAccountId = 999;
    // when(accountRepository.findById(nonExistentAccountId)).thenReturn(Optional.empty());

    // // Act & Assert
    // ResourceNotFoundException exception =
    // assertThrows(ResourceNotFoundException.class, () -> {
    // accountService.getAccountDetail(nonExistentAccountId);
    // });
    // assertEquals("Account not found with id: " + nonExistentAccountId,
    // exception.getMessage());

    // // Verify
    // verify(accountRepository, times(1)).findById(nonExistentAccountId);
    // }

    @Test
    @DisplayName("更新帳戶時，若帳戶不存在應拋出ResourceNotFoundException")
    void updateAccount_whenAccountNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        Integer nonExistentAccountId = 999;
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setId(nonExistentAccountId);
        request.setName("Any Name");
        request.setStatus("Y");

        when(accountRepository.findById(nonExistentAccountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            accountService.updateAccount(request);
        });

        // Verify
        verify(accountRepository, times(1)).findById(nonExistentAccountId);
        verify(orderClient, never()).accountIdIsInOrder(any(Integer.class));
        verify(accountRepository, never()).save(any(Account.class));
    }
    
    @Test
    @DisplayName("更新帳戶狀態為N時，若有關聯訂單應拋出AccountStillHasOrderCanNotBeDeleteException")
    void updateAccount_whenStatusChangeToNAndHasOrder_shouldThrowException() {
        // Arrange
        // activeAccount is set up in @BeforeEach with status "Y" and id 1
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setId(activeAccount.getId());
        request.setName("Updated Name");
        request.setStatus("N");

        // Mock the behavior of accountRepository and orderClient
        // to simulate the scenario where the account has an order
        when(accountRepository.findById(activeAccount.getId())).thenReturn(Optional.of(activeAccount));
        when(orderClient.accountIdIsInOrder(activeAccount.getId())).thenReturn(true);

        // Act & Assert
        assertThrows(AccountStillHasOrderCanNotBeDeleteException.class, () -> accountService.updateAccount(request));

        // Verify
        verify(accountRepository, times(1)).findById(activeAccount.getId());
        verify(orderClient, times(1)).accountIdIsInOrder(activeAccount.getId());
        verify(accountRepository, never()).save(any(Account.class));
    }
    
    @Test
    @DisplayName("刪除帳戶時，若帳戶ID不存在應拋出ResourceNotFoundException")
    void deleteAccount_whenAccountNotFoundInitially_shouldThrowResourceNotFoundException() {
        // Arrange
        Integer nonExistentAccountId = 999;
        when(accountRepository.findById(nonExistentAccountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            accountService.deleteAccount(nonExistentAccountId);
        });

        // Verify
        verify(accountRepository, times(1)).findById(nonExistentAccountId);
        verify(orderClient, never()).accountIdIsInOrder(any(Integer.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("刪除帳戶時，若帳戶已為非活躍狀態(N)應拋出ResourceNotFoundException")
    void deleteAccount_whenAccountAlreadyInactive_shouldThrowResourceNotFoundException() {
        // Arrange
        // inactiveAccount is set up in @BeforeEach with status "N" and id 2
        when(accountRepository.findById(inactiveAccount.getId())).thenReturn(Optional.of(inactiveAccount));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            accountService.deleteAccount(inactiveAccount.getId());
        });

        // Verify
        verify(accountRepository, times(1)).findById(inactiveAccount.getId());
        verify(orderClient, never()).accountIdIsInOrder(any(Integer.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("刪除帳戶時，若有關聯訂單應拋出AccountStillHasOrderCanNotBeDeleteException")
    void deleteAccount_whenHasOrder_shouldThrowException() {
        // Arrange
        when(accountRepository.findById(activeAccount.getId())).thenReturn(Optional.of(activeAccount));
        when(orderClient.accountIdIsInOrder(activeAccount.getId())).thenReturn(true);

        // Act & Assert
        assertThrows(AccountStillHasOrderCanNotBeDeleteException.class,
                () -> accountService.deleteAccount(activeAccount.getId()));

        // Verify
        verify(accountRepository, times(1)).findById(activeAccount.getId());
        verify(orderClient, times(1)).accountIdIsInOrder(activeAccount.getId());
        verify(accountRepository, never()).save(any(Account.class));
    }
}
