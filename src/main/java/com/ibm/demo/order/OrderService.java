package com.ibm.demo.order;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.ibm.demo.account.AccountClient;
import com.ibm.demo.exception.BusinessException;
import com.ibm.demo.order.DTO.CreateOrderRequest;
import com.ibm.demo.order.DTO.GetOrderDetailResponse;
import com.ibm.demo.order.DTO.GetOrderListResponse;
import com.ibm.demo.order.DTO.OrderDeletionPlan;
import com.ibm.demo.order.DTO.OrderLineResponse;
import com.ibm.demo.order.DTO.OrderView;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.internal.AdjustStockRequest;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;
import com.ibm.demo.product.DTO.internal.StockChangeRequest;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.util.PageResponse;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@CircuitBreaker(name = "OrderService")
public class OrderService {
        private final OrderRepository orderRepository;
        private final AccountClient accountClient;
        private final ProductClient productClient;
        private final OrderTransactionalService orderTransactionalService;

        @Bulkhead(name = "order-write")
        @RateLimiter(name = "order-write")
        public Integer createOrder(CreateOrderRequest createOrderRequest) {
                // 驗證帳戶具下單資格（資格規則由帳戶領域負責）
                Integer accountId = createOrderRequest.accountId();
                accountClient.assertCanPlaceOrder(accountId);

                // 驗證並轉換訂單明細，確保同一訂單中同一商品只有一筆明細
                Set<OrderItemRequest> uniqueItems = validateAndConvertToUniqueItems(
                                createOrderRequest.items(),
                                detail -> detail.productId(),
                                detail -> detail.quantity());

                productClient.reserveStock(new StockChangeRequest(uniqueItems));

                // 將資料庫操作委派給交易服務，若失敗則補償歸還庫存
                try {
                        return orderTransactionalService.createOrder(createOrderRequest);
                } catch (Exception e) {
                        runCompensation(
                                        () -> productClient.releaseStock(new StockChangeRequest(uniqueItems)),
                                        e,
                                        "建立訂單失敗後，補償歸還庫存也失敗",
                                        "帳戶ID: " + accountId + ", 商品清單: " + describeItems(uniqueItems));
                        throw e;
                }
        }

        /**
         * 分頁獲取指定帳戶的訂單列表。
         *
         * @param accountId 帳戶 ID
         * @param pageable  分頁參數
         * @return 包含訂單列表資訊的分頁回應
         */
        @Bulkhead(name = "order-read")
        @RateLimiter(name = "order-read")
        public PageResponse<GetOrderListResponse> getOrderListByAccountId(Integer accountId, Pageable pageable) {
                // 交易內載入並萃取為純快照（明細於 session 內載入，避免交易外碰 lazy）；之後的遠端呼叫都在交易外
                Page<OrderView> orderViews = orderTransactionalService.loadOrderViews(accountId, pageable);

                List<OrderItemRequest> allItems = orderViews.getContent().stream()
                                .flatMap(v -> v.items().stream())
                                .toList();
                Map<Integer, GetProductDetailResponse> productMap = fetchProductsOf(allItems);

                Page<GetOrderListResponse> responsePage = orderViews.map(v -> GetOrderListResponse.builder()
                                .orderId(v.orderId())
                                .totalAmount(calculateTotalAmount(v.items(), productMap))
                                .build());

                return PageResponse.from(responsePage);
        }

        @Bulkhead(name = "order-read")
        @RateLimiter(name = "order-read")
        public GetOrderDetailResponse getOrderDetailByOrderId(Integer orderId) {
                // 交易內載入並萃取為純快照（找不到直接噴 404）；之後的遠端呼叫都在交易外
                OrderView view = orderTransactionalService.loadOrderView(orderId);

                // 總金額與明細共用同一份 productMap，不重複遠端查詢
                Map<Integer, GetProductDetailResponse> productMap = fetchProductsOf(view.items());

                return GetOrderDetailResponse.builder()
                                .accountId(view.accountId())
                                .totalAmount(calculateTotalAmount(view.items(), productMap))
                                .items(toOrderLines(view.items(), productMap))
                                .build();
        }

        @Bulkhead(name = "order-write")
        @RateLimiter(name = "order-write")
        public void updateOrder(Integer orderId, UpdateOrderRequest request) {
                // 1. 先驗證請求本身（同一商品只能一筆），失敗即擋下，不必查 DB 或動庫存
                Set<OrderItemRequest> uniqueItems = validateAndConvertToUniqueItems(
                                request.items(),
                                detail -> detail.productId(),
                                detail -> detail.quantity());

                // 2. 交易內載入現有訂單快照（取得原明細與帳戶，避免交易外碰 lazy）
                OrderView view = orderTransactionalService.loadOrderView(orderId);
                Set<OrderItemRequest> originalItems = Set.copyOf(view.items());

                productClient.adjustStock(AdjustStockRequest.builder()
                                .from(originalItems)
                                .to(uniqueItems)
                                .build());

                // 將資料庫操作委派給交易服務（交易內自行載入 managed 實體），失敗則補償反轉庫存操作
                try {
                        orderTransactionalService.updateOrder(orderId, request);
                } catch (Exception e) {
                        // 庫存調整回原狀：from 與 to 互換
                        runCompensation(
                                        () -> productClient.adjustStock(AdjustStockRequest.builder()
                                                        .from(uniqueItems)
                                                        .to(originalItems)
                                                        .build()),
                                        e,
                                        "更新訂單失敗後，補償反轉庫存也失敗",
                                        "訂單ID: " + orderId + ", 帳戶ID: " + view.accountId()
                                                        + ", 原商品清單: " + describeItems(originalItems)
                                                        + ", 新商品清單: " + describeItems(uniqueItems));
                        throw e;
                }
        }

        @Bulkhead(name = "order-write")
        @RateLimiter(name = "order-write")
        public void deleteOrder(Integer orderId) {
                // 1. 交易內載入並驗證狀態，把後續所需資料萃取為純 DTO（避免 lazy 洩漏到交易外、不依賴 OSIV）
                OrderDeletionPlan plan = orderTransactionalService.prepareOrderDeletion(orderId);

                // 2. 先歸還庫存（外部服務調用放在前面且在交易外，失敗時訂單不受影響）
                productClient.releaseStock(new StockChangeRequest(plan.items()));

                // 3. 再刪除訂單，若失敗則補償重新扣回庫存
                try {
                        orderTransactionalService.deleteOrder(orderId, plan.version());
                } catch (Exception e) {
                        runCompensation(
                                        () -> productClient.reserveStock(new StockChangeRequest(plan.items())),
                                        e,
                                        "刪除訂單失敗後，補償重新扣回庫存也失敗",
                                        "訂單ID: " + orderId + ", 帳戶ID: " + plan.accountId()
                                                        + ", 商品清單: " + describeItems(plan.items()));
                        throw e;
                }
        }

        /**
         * 執行補償動作；補償本身也失敗時只記錄 ERROR 要求人工介入，不蓋掉原始異常。
         * 呼叫端在此之後仍須 {@code throw} 原始異常，讓外層看到的是真正的失敗原因。
         *
         * @param compensation   要反轉先前遠端庫存變動的動作
         * @param cause          觸發補償的原始異常
         * @param failureMessage 補償失敗時的訊息開頭（說明是哪個流程）
         * @param context        人工介入時需要的識別資訊（訂單、帳戶、商品清單）
         */
        private void runCompensation(Runnable compensation, Exception cause, String failureMessage, String context) {
                try {
                        compensation.run();
                } catch (Exception compensationEx) {
                        log.error("{}，需人工介入處理。{}, 原始異常: {}, 補償異常: {}",
                                        failureMessage, context, cause.getMessage(), compensationEx.getMessage(),
                                        compensationEx);
                }
        }

        private String describeItems(Collection<OrderItemRequest> items) {
                return items.stream()
                                .map(item -> String.format("商品%d(數量%d)", item.productId(), item.quantity()))
                                .collect(Collectors.joining(", "));
        }

        /** 先收集不重複的商品 ID 再一次查詢（避免 N+1）。 */
        private Map<Integer, GetProductDetailResponse> fetchProductsOf(Collection<OrderItemRequest> items) {
                Set<Integer> productIds = items.stream()
                                .map(OrderItemRequest::productId)
                                .collect(Collectors.toSet());
                return batchGetProductDetails(productIds);
        }

        private List<OrderLineResponse> toOrderLines(List<OrderItemRequest> items,
                        Map<Integer, GetProductDetailResponse> productMap) {
                return items.stream()
                                .map(item -> {
                                        GetProductDetailResponse product = productMap.get(item.productId());
                                        return OrderLineResponse.builder()
                                                        .productId(item.productId())
                                                        .productName(product.name())
                                                        .quantity(item.quantity())
                                                        .productPrice(product.price())
                                                        .build();
                                })
                                .toList();
        }

        private Map<Integer, GetProductDetailResponse> batchGetProductDetails(Set<Integer> productIds) {
                if (productIds == null || productIds.isEmpty()) {
                        return Collections.emptyMap();
                }

                List<GetProductDetailResponse> productList = productClient.getProductDetails(productIds);

                // 將 List 轉換為 Map，方便後續根據 ID 查找
                Map<Integer, GetProductDetailResponse> productMap = productList.stream()
                                .collect(Collectors.toMap(
                                                GetProductDetailResponse::id,
                                                product -> product));

                // 不應該 Throw Exception，除非歷史訂單也不准看停售商品
                return productMap;
        }

        /**
         * 依已批量查詢的商品資訊計算訂單總金額。
         *
         * @param items      訂單明細快照（productId + quantity）
         * @param productMap 已批量查詢的商品資訊 Map
         * @return 訂單總金額
         */
        private BigDecimal calculateTotalAmount(List<OrderItemRequest> items,
                        Map<Integer, GetProductDetailResponse> productMap) {
                return items.stream()
                                .map(item -> {
                                        GetProductDetailResponse product = productMap.get(item.productId());
                                        return product.price().multiply(BigDecimal.valueOf(item.quantity()));
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /**
         * 驗證並轉換訂單明細為唯一商品集合
         * 確保同一訂單中同一商品只有一筆明細
         * 
         * @param items 訂單明細列表
         * @return 唯一的訂單商品集合
         * @throws BusinessException 當存在重複商品時（ErrorCode.INVALID_REQUEST）
         */
        private <T> Set<OrderItemRequest> validateAndConvertToUniqueItems(List<T> items,
                        java.util.function.Function<T, Integer> productIdExtractor,
                        java.util.function.Function<T, Integer> quantityExtractor) {
                // 以 productId 單一鍵判定重複（不含 quantity），確保同商品不同數量也會被攔下
                long distinctProductIds = items.stream()
                                .map(productIdExtractor)
                                .distinct()
                                .count();

                if (distinctProductIds != items.size()) {
                        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                                        "同一訂單中同一商品只能有一筆明細，請合併重複的商品明細後再提交訂單。");
                }

                // 通過驗證後 productId 已唯一，轉換為 OrderItemRequest 集合（每商品一筆）
                return items.stream()
                                .map(item -> OrderItemRequest.builder()
                                                .productId(productIdExtractor.apply(item))
                                                .quantity(quantityExtractor.apply(item))
                                                .build())
                                .collect(Collectors.toSet());
        }

        /**
         * 驗證帳戶是否有關聯的訂單
         * 
         * @param accountId 帳戶ID
         * @return 若帳戶有關聯訂單則返回 true，否則返回 false
         */
        public boolean isActiveAccountInOrder(Integer accountId) {
                return !orderRepository.findByAccountId(accountId).isEmpty();
        }

}
