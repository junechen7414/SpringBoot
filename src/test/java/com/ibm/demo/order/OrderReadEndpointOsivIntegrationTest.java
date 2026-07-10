package com.ibm.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ibm.demo.BaseIntegrationTest;
import com.ibm.demo.account.Account;
import com.ibm.demo.account.AccountRepository;
import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.enums.OrderStatus;
import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.Entity.OrderDetail;
import com.ibm.demo.order.Entity.OrderInfo;
import com.ibm.demo.order.Repository.OrderInfoRepository;
import com.ibm.demo.product.Product;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.ProductRepository;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.util.PageResponse;

/**
 * 迴歸測試：關閉 OSIV 後，非交易的訂單讀取端點在「無環境 session」下組裝回應時，
 * 不得因存取 lazy 的 {@code orderDetails} 而拋 {@code LazyInitializationException}。
 * <p>
 * 這是先前 OSIV 關閉退化(order 端點 500)未被單元/整合測試攔下、僅由下游 E2E 抓到的補課：
 * <ul>
 *   <li>測試方法「不」加 {@code @Transactional}——否則測試級交易會撐開 session，遮蔽問題；</li>
 *   <li>以 {@code @TestPropertySource} 釘住 {@code open-in-view=false}，使本測試永遠以 OSIV
 *       關閉的條件驗證端點的 session 獨立性，不受全域設定變動影響；</li>
 *   <li>{@code ProductClient} 以 {@code @MockitoBean} 取代，聚焦 lazy/session 行為而非跨服務自呼叫。</li>
 * </ul>
 * 於本次重構前（讀取端點在交易外直接碰 {@code getOrderDetails()}）本測試會失敗。
 */
@Tag("IntegrationTest")
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class OrderReadEndpointOsivIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderInfoRepository orderInfoRepository;

    @MockitoBean
    private ProductClient productClient;

    @Test
    @DisplayName("OSIV 關閉下，getOrderDetailByOrderId 於交易外組裝不應拋 LazyInitializationException")
    void getOrderDetail_withoutOsiv_shouldAssembleSafely() {
        Seed seed = seedOrderWithOneDetail();
        stubProductLookup(seed.productId());

        GetOrderDetailResponse resp = orderService.getOrderDetailByOrderId(seed.orderId());

        assertThat(resp.accountId()).isEqualTo(seed.accountId());
        assertThat(resp.orderStatus()).isEqualTo(OrderStatus.CREATED.getCode());
        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).productId()).isEqualTo(seed.productId());
        assertThat(resp.totalAmount()).isEqualByComparingTo(new BigDecimal("200")); // 100 * 2
    }

    @Test
    @DisplayName("OSIV 關閉下，getOrderListByAccountId 於交易外組裝不應拋 LazyInitializationException")
    void getOrderList_withoutOsiv_shouldAssembleSafely() {
        Seed seed = seedOrderWithOneDetail();
        stubProductLookup(seed.productId());

        PageResponse<GetOrderListResponse> page =
                orderService.getOrderListByAccountId(seed.accountId(), PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).orderId()).isEqualTo(seed.orderId());
        assertThat(page.content().get(0).totalAmount()).isEqualByComparingTo(new BigDecimal("200"));
    }

    // --- Helpers ---

    private void stubProductLookup(Integer productId) {
        when(productClient.getProductDetails(anySet())).thenReturn(List.of(
                GetProductDetailResponse.builder()
                        .id(productId)
                        .name("OSIV 讀取測試商品")
                        .price(new BigDecimal("100"))
                        .saleStatus(ProductStatus.AVAILABLE.getCode())
                        .available(100)
                        .build()));
    }

    private Seed seedOrderWithOneDetail() {
        Integer accountId = accountRepository.saveAndFlush(Account.builder()
                .name("OSIV 讀取測試帳戶")
                .status(AccountStatus.ACTIVE.getCode())
                .build()).getId();
        Integer productId = productRepository.saveAndFlush(Product.builder()
                .name("OSIV 讀取測試商品")
                .price(new BigDecimal("100"))
                .saleStatus(ProductStatus.AVAILABLE.getCode())
                .build()).getId();
        OrderInfo order = OrderInfo.builder()
                .accountId(accountId)
                .status(OrderStatus.CREATED.getCode())
                .build();
        order.setOrderDetails(new ArrayList<>());
        order.getOrderDetails().add(OrderDetail.builder()
                .orderInfo(order).productId(productId).quantity(2).build());
        Integer orderId = orderInfoRepository.saveAndFlush(order).getId();
        return new Seed(accountId, productId, orderId);
    }

    private record Seed(Integer accountId, Integer productId, Integer orderId) {
    }
}
