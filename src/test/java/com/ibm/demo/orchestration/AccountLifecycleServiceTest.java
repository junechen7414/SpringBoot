package com.ibm.demo.orchestration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.account.AccountService;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.order.DTO.internal.OrderExistenceResponse;
import com.ibm.demo.order.OrderClient;

/**
 * {@link AccountLifecycleService} 的單元測試：釘住「停用（刪除）帳戶前必須無有效訂單」這條
 * 跨領域守門，以及它與 404 的優先順序。
 *
 * <p>這些案例原本住在 {@code AccountServiceTest}，隨守門邏輯一起搬過來 —— account 領域
 * 不再認識 order，對應的測試也不該在那裡 mock {@link OrderClient}。
 */
@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
@DisplayName("帳戶生命週期跨領域協調")
class AccountLifecycleServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private OrderClient orderClient;

    private AccountLifecycleService accountLifecycleService;

    private static final Integer ACCOUNT_ID = 1;

    @BeforeEach
    void setUp() {
        accountLifecycleService = new AccountLifecycleService(accountService, orderClient);
    }

    // --- Helpers ---

    private void stubAccountExists() {
        when(accountService.getAccountDetail(ACCOUNT_ID)).thenReturn(
                GetAccountDetailResponse.builder().name("Test User").build());
    }

    private void stubHasActiveOrder(boolean hasActiveOrder) {
        when(orderClient.getOrderExistence(ACCOUNT_ID)).thenReturn(new OrderExistenceResponse(hasActiveOrder));
    }

    @Nested
    @DisplayName("刪除帳戶")
    class DeleteAccountTests {

        @Test
        @DisplayName("仍有有效訂單時，應拋出例外且不委派刪除")
        void delete_WhenHasActiveOrder_ShouldThrowAndNotDelegate() {
            stubAccountExists();
            stubHasActiveOrder(true);

            assertThatThrownBy(() -> accountLifecycleService.deleteAccount(ACCOUNT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED)
                    .hasMessageContaining("associated orders");

            verify(accountService, never()).deleteAccount(any());
        }

        @Test
        @DisplayName("無有效訂單時，應委派刪除")
        void delete_WhenNoActiveOrder_ShouldDelegate() {
            stubAccountExists();
            stubHasActiveOrder(false);

            accountLifecycleService.deleteAccount(ACCOUNT_ID);

            verify(accountService).deleteAccount(ACCOUNT_ID);
        }

        @Test
        @DisplayName("帳戶不存在時應直接回 404，不得因為先問到訂單而變成 400")
        void delete_WhenAccountNotFound_ShouldNotCallOrder() {
            when(accountService.getAccountDetail(ACCOUNT_ID))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found with id: 1"));

            assertThatThrownBy(() -> accountLifecycleService.deleteAccount(ACCOUNT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verifyNoInteractions(orderClient);
            verify(accountService, never()).deleteAccount(any());
        }
    }
}
