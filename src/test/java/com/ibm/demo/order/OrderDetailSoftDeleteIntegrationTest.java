package com.ibm.demo.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.ibm.demo.BaseIntegrationTest;
import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 驗證 updateOrder 移除訂單明細時，是透過 {@code @SQLDelete} 走「軟刪」而非 orphanRemoval 的實體 DELETE。
 * 這類行為只能對真實 DB 驗證：unit test 皆 mock 掉 repository / 交易服務，無法觸發 Hibernate 的
 * orphanRemoval 與 {@code @Version} 參數綁定。
 */
@Tag("IntegrationTest")
class OrderDetailSoftDeleteIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderInfoRepository orderInfoRepository;

    @Autowired
    private OrderTransactionalService orderTransactionalService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("更新訂單移除明細時，該明細應被軟刪(DELETED=true、VERSION+1)而非實體 DELETE")
    @Transactional
    void updateOrder_removingDetail_shouldSoftDeleteNotHardDelete() {
        Integer accountId = createAccount();
        Integer productAId = createProduct("商品A");
        Integer productBId = createProduct("商品B");
        OrderInfo order = createOrderWithTwoDetails(accountId, productAId, productBId);
        Integer orderId = order.getId();

        // 更新為只保留 A(數量改 5)、移除 B
        UpdateOrderRequest request = new UpdateOrderRequest(
                OrderStatus.CREATED.getCode(),
                List.of(new UpdateOrderDetailRequest(productAId, 5)));

        orderTransactionalService.updateOrder(orderId, request);
        entityManager.flush(); // 強制 orphanRemoval 觸發的 @SQLDelete UPDATE 落地
        entityManager.clear();

        // B 的實體列仍在(未被硬刪)，且是軟刪狀態
        assertEquals(1, countRows(orderId, productBId, ""), "B 明細列不應被實體刪除");
        assertEquals(1, countRows(orderId, productBId, " AND DELETED = true"), "B 明細列應為軟刪(DELETED=true)");
        assertEquals(1, countRows(orderId, productBId, " AND DELETED_AT IS NOT NULL"), "軟刪應寫入 DELETED_AT");
        assertEquals(1, versionOf(orderId, productBId), "軟刪應遞增 VERSION(0 -> 1)");

        // 透過 @SQLRestriction 過濾後，collection 只看得到 A
        OrderInfo reloaded = orderInfoRepository.findById(orderId).orElseThrow();
        assertEquals(1, reloaded.getOrderDetails().size(), "重新載入後只應剩 A");
        assertEquals(productAId, reloaded.getOrderDetails().get(0).getProductId());
    }

    @Test
    @DisplayName("重加先前被軟刪的同一商品，應新增一列 active，形成(軟刪舊列 + active 新列)多列")
    @Transactional
    void updateOrder_readdingRemovedProduct_shouldKeepMultipleRows() {
        Integer accountId = createAccount();
        Integer productAId = createProduct("商品A");
        Integer productBId = createProduct("商品B");
        Integer orderId = createOrderWithTwoDetails(accountId, productAId, productBId).getId();

        // 第一次更新：移除 B(軟刪)
        orderTransactionalService.updateOrder(orderId,
                new UpdateOrderRequest(OrderStatus.CREATED.getCode(),
                        List.of(new UpdateOrderDetailRequest(productAId, 5))));
        entityManager.flush();
        entityManager.clear();

        // 第二次更新：把 B 加回來
        orderTransactionalService.updateOrder(orderId,
                new UpdateOrderRequest(OrderStatus.CREATED.getCode(),
                        List.of(new UpdateOrderDetailRequest(productAId, 5),
                                new UpdateOrderDetailRequest(productBId, 7))));
        entityManager.flush();
        entityManager.clear();

        // B 有兩列實體(1 軟刪 + 1 active)，其中僅 1 列 active
        assertEquals(2, countRows(orderId, productBId, ""), "B 應累積為兩列實體");
        assertEquals(1, countRows(orderId, productBId, " AND DELETED = false"), "B 僅應有一列 active");

        // 過濾後 collection 看得到 A、B 各一(皆 active)
        OrderInfo reloaded = orderInfoRepository.findById(orderId).orElseThrow();
        assertEquals(2, reloaded.getOrderDetails().size(), "重新載入後應有 active 的 A 與 B");
    }

    @Test
    @DisplayName("以過期 VERSION 移除明細時，@SQLDelete 影響 0 列應觸發樂觀鎖失敗")
    @Transactional
    void updateOrder_removingDetailWithStaleVersion_shouldFailOptimisticLock() {
        Integer accountId = createAccount();
        Integer productAId = createProduct("商品A");
        Integer productBId = createProduct("商品B");
        OrderInfo order = createOrderWithTwoDetails(accountId, productAId, productBId);
        Integer orderId = order.getId();
        Integer bDetailId = order.getOrderDetails().stream()
                .filter(d -> d.getProductId().equals(productBId))
                .findFirst().orElseThrow().getId();

        // 先把訂單載入 persistence context；稍後 updateOrder 於同一交易內 findById 會命中此快取(VERSION=0)
        orderInfoRepository.findById(orderId).orElseThrow();

        // 在 Hibernate 背後直接把 B 列的 VERSION 加 1，使 persistence context 手中的版本(0)過期
        entityManager.createNativeQuery(
                "UPDATE ORDER_PRODUCT_DETAIL SET VERSION = VERSION + 1 WHERE ID = :id")
                .setParameter("id", bDetailId)
                .executeUpdate();

        // 移除 B：orphanRemoval 觸發的 @SQLDelete WHERE VERSION = 0 將影響 0 列
        orderTransactionalService.updateOrder(orderId,
                new UpdateOrderRequest(OrderStatus.CREATED.getCode(),
                        List.of(new UpdateOrderDetailRequest(productAId, 5))));

        // flush 透過 repository proxy，Hibernate 的 StaleObjectStateException 會被轉為 Spring 的樂觀鎖例外
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> orderInfoRepository.flush());
    }

    // --- Helpers ---

    private Integer createAccount() {
        return accountRepository.saveAndFlush(Account.builder()
                .name("軟刪測試帳戶")
                .status(AccountStatus.ACTIVE.getCode())
                .build()).getId();
    }

    private Integer createProduct(String name) {
        return productRepository.saveAndFlush(Product.builder()
                .name(name)
                .price(new BigDecimal("100"))
                .saleStatus(ProductStatus.AVAILABLE.getCode())
                .build()).getId();
    }

    private OrderInfo createOrderWithTwoDetails(Integer accountId, Integer productAId, Integer productBId) {
        OrderInfo order = OrderInfo.builder()
                .accountId(accountId)
                .status(OrderStatus.CREATED.getCode())
                .build();
        order.addOrderDetail(OrderDetail.builder().productId(productAId).quantity(2).build());
        order.addOrderDetail(OrderDetail.builder().productId(productBId).quantity(3).build());
        return orderInfoRepository.saveAndFlush(order); // cascade PERSIST 一併存明細
    }

    /** 以 native query 繞過 {@code @SQLRestriction}，直接數實體列(含已軟刪)。 */
    private long countRows(Integer orderId, Integer productId, String extraCondition) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM ORDER_PRODUCT_DETAIL "
                        + "WHERE ORDER_ID = :oid AND PRODUCT_ID = :pid" + extraCondition)
                .setParameter("oid", orderId)
                .setParameter("pid", productId)
                .getSingleResult();
        return count.longValue();
    }

    /** 讀取(單一)符合條件明細列的 VERSION，同樣繞過 {@code @SQLRestriction}。 */
    private int versionOf(Integer orderId, Integer productId) {
        Number version = (Number) entityManager.createNativeQuery(
                "SELECT VERSION FROM ORDER_PRODUCT_DETAIL "
                        + "WHERE ORDER_ID = :oid AND PRODUCT_ID = :pid")
                .setParameter("oid", orderId)
                .setParameter("pid", productId)
                .getSingleResult();
        return version.intValue();
    }
}
