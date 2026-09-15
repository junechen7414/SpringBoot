package com.ibm.demo.orchestration;

import org.springframework.stereotype.Service;

import com.ibm.demo.account.AccountService;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.order.OrderClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 帳戶生命週期的<b>跨領域協調者</b>：停用與刪除帳戶前，必須確認該帳戶沒有有效訂單。
 *
 * <p><b>為什麼這段邏輯不放在 {@code AccountService}？</b>
 * 「還有訂單就不能停用」這條不變量橫跨 account 與 order 兩個聚合。若由 account 端自行檢查，
 * 就得注入 {@link OrderClient}，而 order 端本來就要透過 {@code AccountClient} 驗證下單資格 ——
 * 兩個 domain 形成循環依賴。帳戶是更上游的概念，它不應該知道世界上有「訂單」這種東西。
 *
 * <p>因此把這個 use case 上提到中立的 orchestration 層，依賴方向變成單向無環：
 * <pre>
 *   orchestration ──→ account
 *         │              ↑
 *         └──→ order ────┘
 * </pre>
 * 這與 {@code OrderService} 協調 account + product 來建立訂單是同一種角色，只是那邊的協調者
 * 剛好住在 order 領域內（因為訂單是它自己的聚合），而「停用帳戶」的協調者無處可歸，故獨立成層。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>：本層會發出跨服務 HTTP 呼叫，若包在交易裡會讓 DB
 * 連線在等待網路期間被佔住。交易邊界留在 {@link AccountService} 的各個方法內 —— 檢查在交易外完成，
 * 真正的寫入才進交易。這也是把呼叫上提後順帶修掉的問題（原本 HTTP 呼叫發生在 account 的交易內）。
 *
 * <p><b>刻意不加 {@code @Bulkhead} / {@code @RateLimiter}</b>：DB 寫入的並發仍由
 * {@link AccountService} 自己的 {@code account-write-with-validation} 隔板控管。若在這裡對同一個
 * 隔板實例再掛一層，單一請求會吃掉兩個許可，等於把並發上限砍半。此處只掛斷路器保護對外的
 * order 呼叫。
 *
 * <p><b>注意</b>：守門搬上來之後，直接呼叫 {@code AccountService.updateAccount} /
 * {@code deleteAccount} 就不再有訂單檢查。外部流量一律經由 {@link AccountLifecycleController}
 * 進入本層，是唯一入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CircuitBreaker(name = "AccountLifecycleService")
public class AccountLifecycleService {

    private final AccountService accountService;
    private final OrderClient orderClient;

    /**
     * 更新帳戶。狀態由啟用轉為停用時，先確認沒有有效訂單。
     *
     * <p>先讀一次帳戶而非直接委派，是為了維持錯誤碼的優先順序：帳戶不存在要回 404，
     * 不能因為「先問到它還有訂單」而變成 400。順帶取得當前狀態，用來判斷狀態是否真的改變。
     */
    public void updateAccount(Integer id, UpdateAccountRequest request) {
        AccountStatus currentStatus = accountService.getAccountDetail(id).status();

        boolean beingDeactivated = request.status() == AccountStatus.INACTIVE
                && currentStatus != AccountStatus.INACTIVE;
        if (beingDeactivated) {
            assertNoActiveOrder(id);
        }

        accountService.updateAccount(id, request);
    }

    /**
     * 軟刪除帳戶。一律先確認沒有有效訂單。
     *
     * <p>同 {@link #updateAccount}：先讀一次以確保帳戶不存在時回 404 而非 400。
     */
    public void deleteAccount(Integer id) {
        accountService.getAccountDetail(id);
        assertNoActiveOrder(id);
        accountService.deleteAccount(id);
    }

    /**
     * 向 order 領域確認帳戶是否仍有有效訂單，有則拒絕本次操作。
     */
    private void assertNoActiveOrder(Integer accountId) {
        if (orderClient.getOrderExistence(accountId).hasActiveOrder()) {
            throw new BusinessException(ErrorCode.ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED,
                    "Account with id: " + accountId + " has associated orders and cannot be set to deactivate.");
        }
    }
}
