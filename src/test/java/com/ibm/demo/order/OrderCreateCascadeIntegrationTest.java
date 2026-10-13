package com.ibm.demo.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.demo.BaseIntegrationTest;
import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.order.DTO.CreateOrderLineRequest;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.OrderView;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import jakarta.persistence.EntityManager;

/**
 * 迴歸測試：{@code createOrder} 必須經由 {@code Order.addOrderLine} 掛載明細，
 * 讓交易內的雙向關聯兩端一致，並由 cascade PERSIST 以單一 save 寫入明細。
 * <p>
 * 修正前的寫法只設子端的 {@code order} 再另外 {@code saveAll}，父端集合在同一個
 * persistence context 內會是空的；DB 資料雖正確，但這份正確性只靠「沒人在交易內讀父端集合」
 * 這條沒寫下來也沒測到的前提。第一個測試就是在釘住這條前提。
 */
@Tag("IntegrationTest")
class OrderCreateCascadeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderTransactionalService orderTransactionalService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("createOrder 後，同一交易內父端集合與子端反向參考皆一致")
    @Transactional
    void createOrder_shouldKeepBothSidesConsistentWithinTransaction() {
        Integer accountId = createAccount();
        Integer productAId = createProduct("建立測試商品A");
        Integer productBId = createProduct("建立測試商品B");

        Integer orderId = orderTransactionalService.createOrder(new CreateOrderRequest(
                accountId,
                List.of(new CreateOrderLineRequest(productAId, 2),
                        new CreateOrderLineRequest(productBId, 3))));

        // createOrder 併入測試交易，因此這裡拿到的是同一個 persistence context 中的實例
        Order order = entityManager.find(Order.class, orderId);
        assertThat(order.getOrderLines())
                .as("父端集合應看得到兩筆明細（修正前為空）")
                .hasSize(2);
        assertThat(order.getOrderLines())
                .allSatisfy(detail -> assertThat(detail.getOrder())
                        .as("子端反向參考應指回同一個父實例")
                        .isSameAs(order));
        assertThat(order.getOrderLines())
                .extracting(OrderLine::getProductId)
                .containsExactlyInAnyOrder(productAId, productBId);
    }

    @Test
    @DisplayName("createOrder 以單一 save 經 cascade PERSIST 寫入明細，且 ORDER_ID 正確")
    @Transactional
    void createOrder_shouldPersistDetailsViaCascade() {
        Integer accountId = createAccount();
        Integer productId = createProduct("建立測試商品");

        Integer orderId = orderTransactionalService.createOrder(new CreateOrderRequest(
                accountId,
                List.of(new CreateOrderLineRequest(productId, 4))));

        entityManager.flush();
        entityManager.clear();

        // 繞過 @SQLRestriction 直接數實體列，確認明細確實落地且外鍵指向該訂單
        Number rows = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM ORDER_PRODUCT_DETAIL WHERE ORDER_ID = :oid")
                .setParameter("oid", orderId)
                .getSingleResult();
        assertThat(rows.longValue()).as("明細應由 cascade 寫入 DB").isEqualTo(1L);

        OrderView view = orderTransactionalService.loadOrderView(orderId);
        assertThat(view.accountId()).isEqualTo(accountId);
        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).productId()).isEqualTo(productId);
        assertThat(view.items().get(0).quantity()).isEqualTo(4);
    }

    // --- Helpers ---

    private Integer createAccount() {
        return accountRepository.saveAndFlush(Account.builder()
                .name("建立訂單測試帳戶")
                .build()).getId();
    }

    private Integer createProduct(String name) {
        return productRepository.saveAndFlush(Product.builder()
                .name(name)
                .price(new BigDecimal("100"))
                .build()).getId();
    }
}
