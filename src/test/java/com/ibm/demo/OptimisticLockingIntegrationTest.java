package com.ibm.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import org.junit.jupiter.api.Tag;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Tag("IntegrationTest")
public class OptimisticLockingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderInfoRepository orderInfoRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("測試商品更新時的樂觀鎖機制 (JPA 標準)")
    @Transactional
    public void testUpdateProductOptimisticLocking() {
        Product product = Product.builder()
                .name("商品樂觀鎖測試")
                .price(new BigDecimal("100"))
                .saleStatus(ProductStatus.AVAILABLE.getCode())
                .build();
        product = productRepository.saveAndFlush(product);
        Integer id = product.getId();

        Product product1 = productRepository.findById(id).get();
        Product product2 = productRepository.findById(id).get();
        // 關鍵：將 product2 脫離 JPA 託管（Detach）。
        // 這樣 product1 更新時，product2 手中的 Version 0 才不會被同步更新為 1。
        // 這是在同一個交易內「人工模擬」出持有舊版資料的客戶端物件。
        entityManager.detach(product2); // 模擬不同的交易上下文

        product1.setPrice(new BigDecimal("110"));
        productRepository.saveAndFlush(product1);

        product2.setPrice(new BigDecimal("120"));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            productRepository.saveAndFlush(product2);
        });
    }

    @Test
    @DisplayName("測試帳戶更新時的樂觀鎖機制 (JPA 標準)")
    @Transactional
    public void testUpdateAccountOptimisticLocking() {
        Account account = Account.builder()
                .name("帳戶樂觀鎖測試")
                .status(AccountStatus.ACTIVE.getCode())
                .build();
        account = accountRepository.saveAndFlush(account);
        Integer id = account.getId();

        Account account1 = accountRepository.findById(id).get();
        Account account2 = accountRepository.findById(id).get();
        // 關鍵：將 account2 脫離 JPA 託管（Detach）。
        // 這樣 account1 更新時，account2 手中的 Version 0 才不會被同步更新為 1。
        // 這是在同一個交易內「人工模擬」出持有舊版資料的客戶端物件。
        entityManager.detach(account2);

        account1.setName("帳戶名稱已更改");
        accountRepository.saveAndFlush(account1);

        account2.setName("嘗試衝突更改");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            accountRepository.saveAndFlush(account2);
        });
    }

    @Test
    @DisplayName("測試訂單更新時的樂觀鎖機制 (JPA 標準)")
    @Transactional
    public void testUpdateOrderOptimisticLocking() {
        // 先建立帳戶以滿足外鍵約束
        Account account = accountRepository.saveAndFlush(Account.builder()
                .name("訂單測試帳戶")
                .status(AccountStatus.ACTIVE.getCode())
                .build());
        
        OrderInfo order = OrderInfo.builder()
                .accountId(account.getId())
                .status(OrderStatus.CREATED.getCode())
                .build();
        order = orderInfoRepository.saveAndFlush(order);
        Integer id = order.getId();

        OrderInfo orderInfo1 = orderInfoRepository.findById(id).get();
        OrderInfo orderInfo2 = orderInfoRepository.findById(id).get();
        // 關鍵：將 orderInfo2 脫離 JPA 託管（Detach）。
        // 這樣 orderInfo1 更新時，orderInfo2 手中的 Version 0 才不會被同步更新為 1。
        // 這是在同一個交易內「人工模擬」出持有舊版資料的客戶端物件。
        entityManager.detach(orderInfo2); // 模擬不同的交易上下文

        orderInfo1.setStatus(OrderStatus.CANCELLED.getCode());
        orderInfoRepository.saveAndFlush(orderInfo1);

        orderInfo2.setStatus(OrderStatus.CANCELLED.getCode());
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            orderInfoRepository.saveAndFlush(orderInfo2);
        });
    }

    @Test
    @DisplayName("測試商品軟刪除時的樂觀鎖機制 (自定義 @Query)")
    @Transactional
    public void testSoftDeleteProductOptimisticLocking() {
        Product product = productRepository.saveAndFlush(Product.builder()
                .name("軟刪除測試")
                .price(new BigDecimal("100"))
                .saleStatus(ProductStatus.AVAILABLE.getCode())
                .build());
        Integer id = product.getId();
        Integer version = product.getVersion();

        // 模擬 A 先更新了資料 (Version 變 1)
        Product product1 = productRepository.findById(id).get();
        product1.setPrice(new BigDecimal("200"));
        productRepository.saveAndFlush(product1);

        // B 嘗試用舊版本刪除，預期傳回 0
        int updated = productRepository.softDeleteById(id, version);
        assertEquals(0, updated, "版本不符時應不更新任何資料");
    }

    @Test
    @DisplayName("測試帳戶軟刪除時的樂觀鎖機制 (自定義 @Query)")
    @Transactional
    public void testSoftDeleteAccountOptimisticLocking() {
        Account account = accountRepository.saveAndFlush(Account.builder()
                .name("帳戶軟刪除測試")
                .status(AccountStatus.ACTIVE.getCode())
                .build());
        Integer id = account.getId();
        Integer version = account.getVersion();

        // 模擬 A 先更新了資料
        Account account1 = accountRepository.findById(id).get();
        account1.setName("已更新");
        accountRepository.saveAndFlush(account1);

        // B 嘗試用舊版本刪除
        int updated = accountRepository.softDeleteById(id, version);
        assertEquals(0, updated);
    }

    @Test
    @DisplayName("測試訂單軟刪除時的樂觀鎖機制 (自定義 @Query)")
    @Transactional
    public void testSoftDeleteOrderOptimisticLocking() {
        // 先建立帳戶以滿足外鍵約束
        Account account = accountRepository.saveAndFlush(Account.builder()
                .name("訂單軟刪除測試帳戶")
                .status(AccountStatus.ACTIVE.getCode())
                .build());
        
        OrderInfo order = orderInfoRepository.saveAndFlush(OrderInfo.builder()
                .accountId(account.getId())
                .status(OrderStatus.CREATED.getCode())
                .build());
        Integer id = order.getId();
        Integer version = order.getVersion();

        // 模擬 A 先更新了資料
        OrderInfo order1 = orderInfoRepository.findById(id).get();
        order1.setStatus(OrderStatus.CANCELLED.getCode());
        orderInfoRepository.saveAndFlush(order1);

        // B 嘗試用舊版本刪除
        int updated = orderInfoRepository.softDeleteById(id, version);
        assertEquals(0, updated);
    }

    /**
     * 驗證 {@code @Modifying(clearAutomatically = true)} 的實際作用。
     * <p>
     * 上面幾支測試驗證的是 <b>DB 層的版本守衛</b>（bulk update 的
     * {@code WHERE version = :version} 命中 0 列）——那跟一級快取無關，加不加
     * {@code clearAutomatically} 結果都一樣。這支測試瞄準的是另一個層次：
     * <b>persistence context 一致性</b>。
     * <p>
     * bulk JPQL update 會繞過 persistence context，不會更新已託管（managed）的實體。
     * 若少了 {@code clearAutomatically = true}，軟刪後在同一交易內再讀，{@code findById}
     * 會直接命中一級快取回傳那個「過時的幽靈物件」（仍為啟用、未刪），繞過
     * {@code @SQLRestriction}。加上參數清空 PC 後，{@code findById} 才會重打 DB 並套用
     * {@code @SQLRestriction}，回傳 empty。
     * <p>
     * 註：此情境純屬 JVM 內一級快取，與資料庫實作無關；換成 Oracle / H2 都一樣，
     * 因此 {@code OracleContainer} 無助於重現它——關鍵在於「同交易內軟刪後再讀同一實體」。
     */
    @Test
    @DisplayName("測試軟刪後清空 persistence context (clearAutomatically = true)")
    @Transactional
    public void testSoftDeleteClearsPersistenceContext() {
        Account account = accountRepository.saveAndFlush(Account.builder()
                .name("PC 一致性測試")
                .status(AccountStatus.ACTIVE.getCode())
                .build());
        Integer id = account.getId();
        Integer version = account.getVersion();

        // 先讓實體進入一級快取（managed）
        Account managed = accountRepository.findById(id).get();
        assertEquals(AccountStatus.ACTIVE.getCode(), managed.getStatus());

        // bulk 軟刪：DB 變 deleted=true / status='N'
        int updated = accountRepository.softDeleteById(id, version);
        assertEquals(1, updated, "版本相符時應更新 1 列");

        // clearAutomatically=true → PC 已清空，findById 重打 DB 套用 @SQLRestriction → empty。
        // 若拿掉該參數，這裡會命中一級快取的過時實體而回傳 present → 斷言失敗。
        assertTrue(accountRepository.findById(id).isEmpty(),
                "軟刪後於同交易內再讀應被 @SQLRestriction 過濾，不應命中過時的一級快取");
    }
}
