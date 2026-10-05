package com.ibm.demo.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ibm.demo.BaseIntegrationTest;
import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.order.DTO.UpdateOrderDetailRequest;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.ProductRepository;
import com.ibm.demo.product.ProductService;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;
import com.ibm.demo.product.DTO.internal.StockChangeRequest;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 驗證 Order Line 的持久化規則（見 docs/adr/0001-soft-delete-only-for-termination.md）：
 * 更新訂單時移除的明細是實體刪除、DB 以 UNIQUE(ORDER_ID, PRODUCT_ID) 守住「同一商品只有一行」，
 * 取消訂單時明細原封不動地保留。
 * 這些行為只能對真實 DB 驗證：unit test 皆 mock 掉 repository / 交易服務，無法觸發 Hibernate 的
 * orphanRemoval、{@code @Version} 刪除條件與 DB 約束。
 */
@Tag("IntegrationTest")
class OrderLineIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderInfoRepository orderInfoRepository;

    @Autowired
    private OrderTransactionalService orderTransactionalService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 測試環境沒有真的 HTTP port 可供 loopback 自呼叫；改為直接轉給本地 ProductService，
    // 庫存變動仍真實寫進 DB，才驗得到 Reservation 是否釋放。
    @MockitoBean
    private ProductClient productClient;

    @Test
    @DisplayName("同一張訂單寫入兩行相同商品的明細時，DB 唯一約束應擋下")
    @Transactional
    void saveOrder_withDuplicateProductLines_shouldBeRejectedByDb() {
        Integer accountId = createAccount();
        Integer productId = createProduct("商品A", 0);
        OrderInfo order = OrderInfo.builder()
                .accountId(accountId)
                .status(OrderStatus.CREATED.getCode())
                .build();
        order.addOrderDetail(OrderDetail.builder().productId(productId).quantity(1).build());
        order.addOrderDetail(OrderDetail.builder().productId(productId).quantity(2).build());

        assertThrows(DataIntegrityViolationException.class, () -> orderInfoRepository.saveAndFlush(order));
    }

    @Test
    @DisplayName("更新訂單移除明細後再加回同一商品，應成功且 DB 只有一列")
    @Transactional
    void updateOrder_removeThenReaddSameProduct_shouldKeepSingleRow() {
        Integer accountId = createAccount();
        Integer productAId = createProduct("商品A", 0);
        Integer productBId = createProduct("商品B", 0);
        Integer orderId = createOrderWithTwoDetails(accountId, productAId, productBId).getId();

        // 第一次更新：移除 B → 實體刪除
        orderTransactionalService.updateOrder(orderId,
                new UpdateOrderRequest(List.of(new UpdateOrderDetailRequest(productAId, 5))));
        entityManager.flush();
        entityManager.clear();
        assertEquals(0, countRows(orderId, productBId), "B 明細列應被實體刪除");

        // 第二次更新：把 B 加回來
        orderTransactionalService.updateOrder(orderId,
                new UpdateOrderRequest(List.of(
                        new UpdateOrderDetailRequest(productAId, 5),
                        new UpdateOrderDetailRequest(productBId, 7))));
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, countRows(orderId, productBId), "B 只應有一列");
        OrderInfo reloaded = orderInfoRepository.findById(orderId).orElseThrow();
        assertEquals(2, reloaded.getOrderDetails().size(), "重新載入後應有 A 與 B");
    }

    @Test
    @DisplayName("以過期 VERSION 移除明細時，實體 DELETE 影響 0 列應觸發樂觀鎖失敗")
    @Transactional
    void updateOrder_removingDetailWithStaleVersion_shouldFailOptimisticLock() {
        Integer accountId = createAccount();
        Integer productAId = createProduct("商品A", 0);
        Integer productBId = createProduct("商品B", 0);
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

        // 移除 B：orphanRemoval 觸發的 DELETE ... WHERE ID = ? AND VERSION = 0 將影響 0 列
        orderTransactionalService.updateOrder(orderId,
                new UpdateOrderRequest(List.of(new UpdateOrderDetailRequest(productAId, 5))));

        // flush 透過 repository proxy，Hibernate 的 StaleObjectStateException 會被轉為 Spring 的樂觀鎖例外
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> orderInfoRepository.flush());
    }

    @Test
    @DisplayName("取消訂單後，明細仍保留在 DB，且保留庫存已釋放")
    void deleteOrder_shouldKeepOrderLinesAndReleaseReservation() {
        // 不加 @Transactional：deleteOrder 的各段交易要真的 commit，之後才能直接查資料表驗證
        doAnswer(invocation -> {
            productService.releaseStock(invocation.<StockChangeRequest>getArgument(0).items());
            return null;
        }).when(productClient).releaseStock(any(StockChangeRequest.class));

        Integer accountId = createAccount();
        Integer productAId = createProduct("商品A", 10);
        Integer productBId = createProduct("商品B", 10);
        Integer orderId = createOrderWithTwoDetails(accountId, productAId, productBId).getId();
        // createOrderWithTwoDetails 的數量：A=2、B=3，照真實流程先預留
        productService.reserveStock(Set.of(
                new OrderItemRequest(productAId, 2),
                new OrderItemRequest(productBId, 3)));

        orderService.deleteOrder(orderId);

        assertEquals(1, countRows(orderId, productAId), "A 明細列應原封不動地保留");
        assertEquals(1, countRows(orderId, productBId), "B 明細列應原封不動地保留");
        assertEquals(0, reservedOf(productAId), "A 的保留量應已釋放");
        assertEquals(0, reservedOf(productBId), "B 的保留量應已釋放");
        assertEquals(10, availableOf(productAId), "A 的可用量應回到預留前");
        assertEquals(10, availableOf(productBId), "B 的可用量應回到預留前");
    }

    // --- Helpers ---

    private Integer createAccount() {
        return accountRepository.saveAndFlush(Account.builder()
                .name("明細測試帳戶")
                .status(AccountStatus.ACTIVE.getCode())
                .build()).getId();
    }

    private Integer createProduct(String name, int available) {
        return productRepository.saveAndFlush(Product.builder()
                .name(name)
                .price(new BigDecimal("100"))
                .saleStatus(ProductStatus.AVAILABLE.getCode())
                .available(available)
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

    /** 直接數資料表的列；ORDER_INFO 的 @SQLRestriction 在取消後會把整張訂單濾掉，不能走 repository。 */
    private int countRows(Integer orderId, Integer productId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ORDER_PRODUCT_DETAIL WHERE ORDER_ID = ? AND PRODUCT_ID = ?",
                Integer.class, orderId, productId);
    }

    private int reservedOf(Integer productId) {
        return jdbcTemplate.queryForObject("SELECT RESERVED FROM PRODUCT WHERE ID = ?", Integer.class, productId);
    }

    private int availableOf(Integer productId) {
        return jdbcTemplate.queryForObject("SELECT AVAILABLE FROM PRODUCT WHERE ID = ?", Integer.class, productId);
    }
}
