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
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.order.OrderInfo;
import com.ibm.demo.order.OrderInfoRepository;
import com.ibm.demo.order.OrderTransactionalService;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 釘住所有把「狀態值」嵌在 SQL／JPQL 字串裡的地方 —— entity 上 {@code @SQLRestriction} 的過濾條件，
 * 以及 soft delete 的 {@code @Query} 寫進去的狀態。
 *
 * <p>這些都是 annotation 裡的字串：編譯器與型別系統都看不進去，改了 {@code OrderStatus} 之類的
 * 列舉卻漏改字串（或反之）不會有任何編譯錯誤，只會讓資料在執行期靜靜地查不到、或被寫成錯誤狀態。
 * 這些字串已改成由 {@code XxxStatus.Codes} 串接，本測試則從另一端驗證串出來的 SQL 對真實 DB 成立。
 *
 * <p>過濾條件的案例都同時斷言兩邊 —— 目標狀態的列查得到、非目標狀態的列查不到。只驗後者的話，
 * 連 {@code 1 = 0} 這種把全部資料擋掉的條件都會通過。
 *
 * <p>每次 {@code flush} 後都 {@code clear}：{@code @SQLRestriction} 只作用於 SQL SELECT，
 * 若 persistence context 還握著剛存的實體，{@code findById} 會直接命中一級快取而不發 SQL，
 * 這條過濾就被繞過了，測試會假性通過。
 */
@Tag("IntegrationTest")
class SqlStatusIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderInfoRepository orderInfoRepository;

    @Autowired
    private OrderTransactionalService orderTransactionalService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Account：狀態為 ACTIVE 的列查得到，INACTIVE 的列雖實體存在但查不到")
    @Transactional
    void account_onlyActiveRowsAreVisible() {
        Integer activeId = insertAccount(AccountStatus.ACTIVE);
        Integer inactiveId = insertAccount(AccountStatus.INACTIVE);

        assertThat(accountRepository.findById(activeId)).as("ACTIVE 帳戶應查得到").isPresent();
        assertThat(accountRepository.findById(inactiveId)).as("INACTIVE 帳戶應被 @SQLRestriction 濾掉").isEmpty();
        assertThat(countRows("ACCOUNT", inactiveId)).as("被濾掉的只是查詢結果，實體列仍在").isEqualTo(1);
    }

    @Test
    @DisplayName("Product：銷售狀態為 AVAILABLE 的列查得到，UNAVAILABLE 的列雖實體存在但查不到")
    @Transactional
    void product_onlyAvailableRowsAreVisible() {
        Integer availableId = insertProduct("上架商品", ProductStatus.AVAILABLE);
        Integer unavailableId = insertProduct("下架商品", ProductStatus.UNAVAILABLE);

        assertThat(productRepository.findById(availableId)).as("AVAILABLE 商品應查得到").isPresent();
        assertThat(productRepository.findById(unavailableId)).as("UNAVAILABLE 商品應被 @SQLRestriction 濾掉").isEmpty();
        assertThat(countRows("PRODUCT", unavailableId)).as("被濾掉的只是查詢結果，實體列仍在").isEqualTo(1);
    }

    @Test
    @DisplayName("OrderInfo：狀態為 CREATED 的列查得到，CANCELLED 的列雖實體存在但查不到")
    @Transactional
    void orderInfo_onlyCreatedRowsAreVisible() {
        Integer accountId = insertAccount(AccountStatus.ACTIVE);
        Integer createdId = insertOrder(accountId, OrderStatus.CREATED);
        Integer cancelledId = insertOrder(accountId, OrderStatus.CANCELLED);

        assertThat(orderInfoRepository.findById(createdId)).as("CREATED 訂單應查得到").isPresent();
        assertThat(orderInfoRepository.findById(cancelledId)).as("CANCELLED 訂單應被 @SQLRestriction 濾掉").isEmpty();
        assertThat(countRows("ORDER_INFO", cancelledId)).as("被濾掉的只是查詢結果，實體列仍在").isEqualTo(1);
    }

    /**
     * 由上一個案例直接推導出的後果，特別釘起來：{@code prepareOrderDeletion} 內那段
     * 「狀態非 CREATED 就拋 ORDER_STATUS_INVALID」對走 repository 的呼叫端是不可達的 ——
     * 非 CREATED 的訂單在 {@code findById} 階段就已經被濾掉，先撞上 RESOURCE_NOT_FOUND。
     * 單元測試看不出這件事，因為那裡的 {@code findById} 是 mock 出來的。
     */
    @Test
    @DisplayName("刪除 CANCELLED 訂單會先撞 RESOURCE_NOT_FOUND，而非 ORDER_STATUS_INVALID")
    @Transactional
    void prepareOrderDeletion_onCancelledOrder_failsAsNotFound() {
        Integer accountId = insertAccount(AccountStatus.ACTIVE);
        Integer cancelledId = insertOrder(accountId, OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderTransactionalService.prepareOrderDeletion(cancelledId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("軟刪除帳戶應把狀態寫成 INACTIVE")
    @Transactional
    void softDeleteAccount_writesInactiveStatus() {
        Integer id = insertAccount(AccountStatus.ACTIVE);

        assertThat(accountRepository.softDeleteById(id, 0)).as("應更新到 1 列（version 相符）").isEqualTo(1);
        assertThat(statusOf("ACCOUNT", "STATUS", id))
                .isEqualTo(AccountStatus.INACTIVE.getCode());
    }

    @Test
    @DisplayName("軟刪除商品應把銷售狀態寫成 UNAVAILABLE")
    @Transactional
    void softDeleteProduct_writesUnavailableStatus() {
        Integer id = insertProduct("待軟刪商品", ProductStatus.AVAILABLE);

        assertThat(productRepository.softDeleteById(id, 0)).as("應更新到 1 列（version 相符）").isEqualTo(1);
        assertThat(statusOf("PRODUCT", "SALE_STATUS", id))
                .isEqualTo(String.valueOf(ProductStatus.UNAVAILABLE.getCode()));
    }

    @Test
    @DisplayName("軟刪除訂單應把狀態寫成 CANCELLED")
    @Transactional
    void softDeleteOrder_writesCancelledStatus() {
        Integer accountId = insertAccount(AccountStatus.ACTIVE);
        Integer id = insertOrder(accountId, OrderStatus.CREATED);

        assertThat(orderInfoRepository.softDeleteById(id, 0)).as("應更新到 1 列（version 相符）").isEqualTo(1);
        assertThat(statusOf("ORDER_INFO", "STATUS", id))
                .isEqualTo(String.valueOf(OrderStatus.Codes.CANCELLED));
    }

    // --- Helpers ---

    private Integer insertAccount(AccountStatus status) {
        Integer id = accountRepository.saveAndFlush(Account.builder()
                .name("SQLRestriction 測試帳戶-" + status.name())
                .status(status.getCode())
                .build()).getId();
        entityManager.clear();
        return id;
    }

    private Integer insertProduct(String name, ProductStatus status) {
        Integer id = productRepository.saveAndFlush(Product.builder()
                .name(name + "-" + System.nanoTime())
                .price(new BigDecimal("100"))
                .saleStatus(status.getCode())
                .build()).getId();
        entityManager.clear();
        return id;
    }

    private Integer insertOrder(Integer accountId, OrderStatus status) {
        Integer id = orderInfoRepository.saveAndFlush(OrderInfo.builder()
                .accountId(accountId)
                .status(status.getCode())
                .build()).getId();
        entityManager.clear();
        return id;
    }

    /** 以 native query 繞過 {@code @SQLRestriction}，讀取軟刪後實際落地的狀態值。 */
    private String statusOf(String table, String column, Integer id) {
        Object value = entityManager
                .createNativeQuery("SELECT " + column + " FROM " + table + " WHERE ID = :id")
                .setParameter("id", id)
                .getSingleResult();
        return String.valueOf(value);
    }

    /** 以 native query 繞過 {@code @SQLRestriction}，確認被濾掉的列確實還在 DB 裡。 */
    private long countRows(String table, Integer id) {
        Number count = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE ID = :id")
                .setParameter("id", id)
                .getSingleResult();
        return count.longValue();
    }
}
