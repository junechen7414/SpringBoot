package com.ibm.demo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

@SpringBootTest
@ActiveProfiles("test") // 使用 test profile，確保使用測試專用的資料庫設定
public class OptimisticLockingIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderInfoRepository orderInfoRepository;

    @Test
    public void testProductOptimisticLocking() {
        // 1. 準備資料：新增一筆商品
        // 使用 saveAndFlush() 確保資料立即寫入資料庫並取得 ID 與初始 Version (0)
        Product product = Product.builder()
                .name("商品樂觀鎖測試")
                .price(new BigDecimal("100"))
                .saleStatus(ProductStatus.AVAILABLE.getCode())
                .build();
        product = productRepository.saveAndFlush(product);
        Integer id = product.getId();

        // 2. 模擬併發讀取：Transaction A 與 Transaction B 同時讀取到同一筆資料 (version 皆為 0)
        Product product1 = productRepository.findById(id).get();
        Product product2 = productRepository.findById(id).get();

        // 3. 第一人更新成功：Transaction A 進行修改並成功儲存 (資料庫 version 變成 1)
        product1.setPrice(new BigDecimal("110"));
        productRepository.saveAndFlush(product1);

        // 4. 第二人更新失敗：Transaction B 持舊版本 (version 0) 嘗試更新，預期觸發衝突
        product2.setPrice(new BigDecimal("120"));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            productRepository.saveAndFlush(product2);
        });
    }

    @Test
    public void testAccountOptimisticLocking() {
        // 1. 準備資料：新增一筆帳戶
        Account account = Account.builder()
                .name("帳戶樂觀鎖測試")
                .status(AccountStatus.ACTIVE.getCode())
                .build();
        account = accountRepository.saveAndFlush(account);
        Integer id = account.getId();

        // 2. 模擬併發讀取
        Account account1 = accountRepository.findById(id).get();
        Account account2 = accountRepository.findById(id).get();

        // 3. 第一人更新成功
        account1.setName("帳戶名稱已更改");
        accountRepository.saveAndFlush(account1);

        // 4. 第二人持舊版本更新失敗
        account2.setName("嘗試衝突更改");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            accountRepository.saveAndFlush(account2);
        });
    }

    @Test
    public void testOrderOptimisticLocking() {
        // 1. 準備資料：新增一筆訂單 (OrderInfo)
        OrderInfo order = OrderInfo.builder()
                .accountId(999) // 測試用虛擬
                // ID
                .status(OrderStatus.CREATED.getCode())
                .build();
        order = orderInfoRepository.saveAndFlush(order);
        Integer id = order.getId();

        // 2. 模擬併發讀取
        OrderInfo orderInfo1 = orderInfoRepository.findById(id).get();
        OrderInfo orderInfo2 = orderInfoRepository.findById(id).get();

        // 3. 第一人更新成功
        orderInfo1.setStatus(OrderStatus.CANCELLED.getCode());
        orderInfoRepository.saveAndFlush(orderInfo1);

        // 4. 第二人持舊版本更新失敗
        orderInfo2.setStatus(OrderStatus.CANCELLED.getCode());
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            orderInfoRepository.saveAndFlush(orderInfo2);
        });
    }
}
