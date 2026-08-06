package com.ibm.demo.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.demo.BaseIntegrationTest;
import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.order.DTO.CreateOrderDetailRequest;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.OrderTransactionalService;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import jakarta.persistence.EntityManager;

/**
 * 迴歸測試：{@link AuditMetadata} 的 CREATED_AT / UPDATED_AT 必須真的被寫進 DB。
 * <p>
 * 修正前 {@code @EntityListeners(AuditingEntityListener.class)} 標在 {@code @Embeddable}
 * 的 AuditMetadata 上 —— JPA 只在 {@code @Entity} / {@code @MappedSuperclass} 上處理它，
 * 標在 embeddable 會被靜默忽略，於是每筆 insert 的兩個時間欄位都綁 null。因為 V1 migration
 * 沒有 NOT NULL，DB 不會擋，這個錯誤只能從 SQL bind log 或這支測試看出來。
 * <p>
 * 斷言刻意下到 native query（而非只看記憶體中的實體）：listener 是在 flush 時才動作，
 * 只斷言 java 物件無法區分「有寫進 DB」與「只改了記憶體」。
 */
@Tag("IntegrationTest")
class AuditMetadataIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderTransactionalService orderTransactionalService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("insert 應把 CREATED_AT / UPDATED_AT 寫進 DB（修正前兩者皆 null）")
    @Transactional
    void insert_shouldPersistAuditTimestamps() {
        Account saved = accountRepository.saveAndFlush(Account.builder()
                .name("審計測試帳戶")
                .status(AccountStatus.ACTIVE.getCode())
                .build());

        assertThat(saved.getAuditMetadata().getCreatedAt()).as("createdAt 應由 auditing 填入").isNotNull();
        assertThat(saved.getAuditMetadata().getUpdatedAt()).as("updatedAt 應由 auditing 填入").isNotNull();
        assertAuditColumnsNotNull("ACCOUNT", saved.getId());
    }

    @Test
    @DisplayName("update 應推進 UPDATED_AT，且不動 CREATED_AT")
    @Transactional
    void update_shouldAdvanceUpdatedAtOnly() {
        Account account = accountRepository.saveAndFlush(Account.builder()
                .name("審計測試帳戶")
                .status(AccountStatus.ACTIVE.getCode())
                .build());
        LocalDateTime createdAt = account.getAuditMetadata().getCreatedAt();
        LocalDateTime firstUpdatedAt = account.getAuditMetadata().getUpdatedAt();

        account.setName("審計測試帳戶（改名）");
        accountRepository.saveAndFlush(account);

        assertThat(account.getAuditMetadata().getUpdatedAt())
                .as("@LastModifiedDate 應在 @PreUpdate 重新取值")
                .isAfter(firstUpdatedAt);
        assertThat(account.getAuditMetadata().getCreatedAt())
                .as("CREATED_AT 標了 updatable = false，不應被改動")
                .isEqualTo(createdAt);
    }

    @Test
    @DisplayName("cascade PERSIST 寫入的明細也要有審計時間（訂單建立的實際路徑）")
    @Transactional
    void cascadePersistedChild_shouldPersistAuditTimestamps() {
        Integer accountId = accountRepository.saveAndFlush(Account.builder()
                .name("審計測試帳戶")
                .status(AccountStatus.ACTIVE.getCode())
                .build()).getId();
        Integer productId = productRepository.saveAndFlush(Product.builder()
                .name("審計測試商品")
                .price(new BigDecimal("100"))
                .saleStatus(ProductStatus.AVAILABLE.getCode())
                .build()).getId();

        Integer orderId = orderTransactionalService.createOrder(new CreateOrderRequest(
                accountId, List.of(new CreateOrderDetailRequest(productId, 2))));
        entityManager.flush();

        assertAuditColumnsNotNull("ORDER_INFO", orderId);

        // 明細不是自己被 save 的，而是由 OrderInfo 的 cascade PERSIST 帶進來；
        // listener 掛在 OrderDetail 自己身上才會生效（不會從父端繼承）。
        Object[] detail = (Object[]) entityManager.createNativeQuery(
                "SELECT CREATED_AT, UPDATED_AT FROM ORDER_PRODUCT_DETAIL WHERE ORDER_ID = :id")
                .setParameter("id", orderId)
                .getSingleResult();
        assertThat(detail[0]).as("ORDER_PRODUCT_DETAIL.CREATED_AT").isNotNull();
        assertThat(detail[1]).as("ORDER_PRODUCT_DETAIL.UPDATED_AT").isNotNull();
    }

    /** 直接讀 DB 欄位，繞過 persistence context 與 @SQLRestriction。 */
    private void assertAuditColumnsNotNull(String table, Integer id) {
        Object[] row = (Object[]) entityManager.createNativeQuery(
                "SELECT CREATED_AT, UPDATED_AT FROM " + table + " WHERE ID = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(row[0]).as(table + ".CREATED_AT").isNotNull();
        assertThat(row[1]).as(table + ".UPDATED_AT").isNotNull();
    }
}
