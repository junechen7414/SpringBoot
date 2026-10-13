package com.ibm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.order.Order;
import com.ibm.demo.order.OrderRepository;
import com.ibm.demo.order.OrderTransactionalService;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 釘住三個終止動作（Deactivate／Delist／Cancel）落到 DB 的樣子：各 repository 的 {@code softDeleteById}
 * 寫入 {@code DELETED}／{@code DELETED_AT}，entity 上的 {@code @SQLRestriction("DELETED = false")}
 * 把它濾掉。終止狀態只由 {@code DELETED} 記錄（見 docs/adr/0001-soft-delete-only-for-termination.md）。
 *
 * <p>兩者都是 annotation 裡的字串，編譯器看不進去，所以要對真實 DB 驗證。每個案例同時斷言兩邊 ——
 * 沒終止的列查得到、終止的列查不到。只驗後者的話，連 {@code 1 = 0} 這種把全部資料擋掉的條件都會通過。
 *
 * <p>每次寫入後都 {@code clear}：{@code @SQLRestriction} 只作用於 SQL SELECT，若 persistence context
 * 還握著剛存的實體，{@code findById} 會直接命中一級快取而不發 SQL，這條過濾就被繞過了，測試會假性通過。
 * {@code softDeleteById} 本身帶 {@code clearAutomatically = true}，插入資料則由 helper 自己 clear。
 */
@Tag("IntegrationTest")
class SoftDeleteIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderTransactionalService orderTransactionalService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Account：停用後查不到，但資料列仍在且 DELETED／DELETED_AT 已寫入")
    @Transactional
    void account_softDeletedRowIsHiddenButKept() {
        Integer keptId = insertAccount("保留帳戶");
        Integer deletedId = insertAccount("停用帳戶");

        assertThat(accountRepository.softDeleteById(deletedId, 0)).as("應更新到 1 列（version 相符）").isEqualTo(1);

        assertThat(accountRepository.findById(keptId)).as("未停用的帳戶應查得到").isPresent();
        assertThat(accountRepository.findById(deletedId)).as("停用的帳戶應被 @SQLRestriction 濾掉").isEmpty();
        assertSoftDeletedRow("ACCOUNT", deletedId);
    }

    @Test
    @DisplayName("Product：下架後查不到，但資料列仍在且 DELETED／DELETED_AT 已寫入")
    @Transactional
    void product_softDeletedRowIsHiddenButKept() {
        Integer keptId = insertProduct("保留商品");
        Integer deletedId = insertProduct("下架商品");

        assertThat(productRepository.softDeleteById(deletedId, 0)).as("應更新到 1 列（version 相符）").isEqualTo(1);

        assertThat(productRepository.findById(keptId)).as("未下架的商品應查得到").isPresent();
        assertThat(productRepository.findById(deletedId)).as("下架的商品應被 @SQLRestriction 濾掉").isEmpty();
        assertSoftDeletedRow("PRODUCT", deletedId);
    }

    @Test
    @DisplayName("Order：取消後查不到，但資料列仍在且 DELETED／DELETED_AT 已寫入")
    @Transactional
    void order_softDeletedRowIsHiddenButKept() {
        Integer accountId = insertAccount("訂單測試帳戶");
        Integer keptId = insertOrder(accountId);
        Integer cancelledId = insertOrder(accountId);

        assertThat(orderRepository.softDeleteById(cancelledId, 0)).as("應更新到 1 列（version 相符）").isEqualTo(1);

        assertThat(orderRepository.findById(keptId)).as("未取消的訂單應查得到").isPresent();
        assertThat(orderRepository.findById(cancelledId)).as("取消的訂單應被 @SQLRestriction 濾掉").isEmpty();
        assertSoftDeletedRow("ORDER_INFO", cancelledId);
    }

    /**
     * 由上一個案例推導出的後果，特別釘起來：已取消的訂單在 {@code prepareOrderDeletion} 的
     * {@code findById} 階段就被濾掉，所以「再取消一次」回的是 RESOURCE_NOT_FOUND。
     * 單元測試看不出這件事，因為那裡的 {@code findById} 是 mock 出來的。
     */
    @Test
    @DisplayName("再次取消已取消的訂單會撞 RESOURCE_NOT_FOUND")
    @Transactional
    void prepareOrderDeletion_onCancelledOrder_failsAsNotFound() {
        Integer accountId = insertAccount("重複取消測試帳戶");
        Integer cancelledId = insertOrder(accountId);
        orderRepository.softDeleteById(cancelledId, 0);

        assertThatThrownBy(() -> orderTransactionalService.prepareOrderDeletion(cancelledId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    // --- Helpers ---

    private Integer insertAccount(String name) {
        Integer id = accountRepository.saveAndFlush(Account.builder()
                .name(name)
                .build()).getId();
        entityManager.clear();
        return id;
    }

    private Integer insertProduct(String name) {
        Integer id = productRepository.saveAndFlush(Product.builder()
                .name(name + "-" + System.nanoTime())
                .price(new BigDecimal("100"))
                .build()).getId();
        entityManager.clear();
        return id;
    }

    private Integer insertOrder(Integer accountId) {
        Integer id = orderRepository.saveAndFlush(Order.builder()
                .accountId(accountId)
                .build()).getId();
        entityManager.clear();
        return id;
    }

    /** 以 native query 繞過 {@code @SQLRestriction}，確認被濾掉的列還在，且終止標記確實落地。 */
    private void assertSoftDeletedRow(String table, Integer id) {
        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT DELETED, DELETED_AT FROM " + table + " WHERE ID = :id")
                .setParameter("id", id)
                .getSingleResult();
        // Oracle 的 BOOLEAN 與 H2 回傳的 Java 型別不同（Boolean／數字），比字串形式即可
        assertThat(String.valueOf(row[0])).as(table + ".DELETED").isIn("true", "1");
        assertThat(row[1]).as(table + ".DELETED_AT").isNotNull();
    }
}
