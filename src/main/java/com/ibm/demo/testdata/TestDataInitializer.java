package com.ibm.demo.testdata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class TestDataInitializer implements CommandLineRunner {
    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception{
        List<Product> products = new ArrayList<>();
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            products.add(Product.builder()
                    .name("Product " + i)
                    .price(BigDecimal.valueOf((long) i * i))
                    .available(i * i +888888888)
                    .saleStatus(ProductStatus.AVAILABLE.getCode())
                    .build());
            accounts.add(Account.builder()
                    .name("Account " + i)
                    .status(AccountStatus.ACTIVE.getCode())
                    .build());
        }
        productRepository.saveAll(products);
        accountRepository.saveAll(accounts);
    }
}
