package com.ibm.demo.testdata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;
import com.ibm.demo.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TestDataService {
    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void createOrderPrecondition(Integer count) {
        if (count <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Count must be positive");
        }
        
        List<Product> products = new ArrayList<>();
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            products.add(Product.builder()
                    .name("Product " + i)
                    .price(BigDecimal.valueOf((long) i * i))
                    .available(i * i +888888888)
                    .build());
            accounts.add(Account.builder()
                    .name("Account " + i)
                    .build());
        }
        productRepository.saveAll(products);
        accountRepository.saveAll(accounts);
    }

}
