package com.ibm.demo.testdata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * 測試資料初始化器
 *
 * 僅在 dev profile 啟用，用於在開發環境中自動建立測試資料。
 * 採用資料存在性檢查機制，避免重複插入資料。
 *
 * 注意事項：
 * 1. 此類別僅用於開發環境，生產環境應使用資料庫遷移工具（如 Flyway）
 * 2. 配合 ddl-auto: update 策略，確保資料在應用重啟後仍然保留
 * 3. 若需重置測試資料，請手動清空資料庫或使用 ddl-auto: create-drop
 */
@Component
@Profile({"dev"})
@RequiredArgsConstructor
public class TestDataInitializer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(TestDataInitializer.class);
    
    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        logger.info("=== 開始檢查測試資料初始化狀態 ===");
        
        // 檢查資料是否已存在，避免重複插入
        long accountCount = accountRepository.count();
        long productCount = productRepository.count();
        
        logger.info("當前資料庫狀態 - 帳戶數量: {}, 商品數量: {}", accountCount, productCount);
        
        if (accountCount > 0 && productCount > 0) {
            logger.info("測試資料已存在，跳過初始化流程");
            return;
        }
        
        logger.info("開始建立測試資料...");
        initializeTestData();
        logger.info("=== 測試資料初始化完成 ===");
    }

    /**
     * 建立測試資料
     *
     * 建立 100 筆帳戶與 100 筆商品資料
     */
    private void initializeTestData() {
        List<Product> products = new ArrayList<>();
        List<Account> accounts = new ArrayList<>();
        
        for (int i = 1; i <= 100; i++) {
            products.add(Product.builder()
                    .name("Product " + i)
                    .price(BigDecimal.valueOf((long) i * i))
                    .available(i * i + 888888888)
                    .saleStatus(ProductStatus.AVAILABLE.getCode())
                    .build());
                    
            accounts.add(Account.builder()
                    .name("Account " + i)
                    .status(AccountStatus.ACTIVE.getCode())
                    .build());
        }
        
        logger.info("儲存 {} 筆商品資料...", products.size());
        productRepository.saveAll(products);
        
        logger.info("儲存 {} 筆帳戶資料...", accounts.size());
        accountRepository.saveAll(accounts);
        
        logger.info("測試資料建立成功 - 帳戶: {}, 商品: {}", accounts.size(), products.size());
    }
}
