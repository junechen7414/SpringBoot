package com.ibm.demo.order;

import java.math.BigDecimal;
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
import com.ibm.demo.order.DTO.OrderItemDTO;
import com.ibm.demo.order.DTO.OrderView;
import com.ibm.demo.order.DTO.UpdateOrderRequest;
import com.ibm.demo.product.ProductClient;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.internal.AdjustStockRequest;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;
import com.ibm.demo.exception.ErrorCode;
import com.ibm.demo.util.PageResponse;
import com.ibm.demo.util.ServiceValidator;

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
        private final OrderInfoRepository orderInfoRepository;
        private final AccountClient accountClient;
        private final ProductClient productClient;
        private final OrderTransactionalService orderTransactionalService;

        /**
         * 注入Repository和Client，已用lombok註解RequiredArgsConstructor定義建構子。
         * 
         * @param orderInfoRepository   訂單主檔資料庫存取介面
         * @param orderDetailRepository 訂單明細資料庫存取介面
         * @param accountClient         帳戶服務的Client，用於驗證帳戶狀態
         * @param productClient         商品服務的Client，用於驗證商品庫存和獲取商品資訊
         */

        /**
         * @param createOrderRequest
         */
        @Bulkhead(name = "order-write")
        @RateLimiter(name = "order-write")
        public Integer createOrder(CreateOrderRequest createOrderRequest) {
                ServiceValidator.validateNotNull(createOrderRequest, "Create order request");
                ServiceValidator.validateNotNull(createOrderRequest.accountId(), "Account ID");
                ServiceValidator.validateNotEmpty(createOrderRequest.items(), "Order details");
                // 驗證帳戶具下單資格（資格規則由帳戶領域負責）
                Integer accountId = createOrderRequest.accountId();
                accountClient.assertCanPlaceOrder(accountId);

                // 驗證並轉換訂單明細，確保同一訂單中同一商品只有一筆明細
                Set<OrderItemRequest> uniqueItems = validateAndConvertToUniqueItems(
                                createOrderRequest.items(),
                                detail -> detail.productId(),
                                detail -> detail.quantity());

                productClient.reserveStock(uniqueItems);

                // 將資料庫操作委派給交易服務，若失敗則補償歸還庫存
                try {
                        return orderTransactionalService.createOrder(createOrderRequest);
                } catch (Exception e) {
                        // 補償: 釋放已預留的庫存
                        try {
                                productClient.releaseStock(uniqueItems);
                        } catch (Exception compensationEx) {
                                log.error("建立訂單失敗後，補償歸還庫存也失敗，需人工介入處理。" +
                                                "帳戶ID: {}, 商品清單: {}, 原始異常: {}, 補償異常: {}",
                                                createOrderRequest.accountId(),
                                                uniqueItems.stream()
                                                                .map(item -> String.format("商品%d(數量%d)",
                                                                                item.productId(), item.quantity()))
                                                                .collect(Collectors.joining(", ")),
                                                e.getMessage(),
                                                compensationEx.getMessage(),
                                                compensationEx);
                        }
                        throw e;
                }
        }

        @Bulkhead(name = "order-read")
        @RateLimiter(name = "order-read")
        /**
         * 分頁獲取指定帳戶的訂單列表。
         *
         * @param accountId 帳戶 ID
         * @param pageable  分頁參數
         * @return 包含訂單列表資訊的分頁回應
         */
        public PageResponse<GetOrderListResponse> getOrderListByAccountId(Integer accountId, Pageable pageable) {
                ServiceValidator.validateNotNull(accountId, "Account ID");
                // 1. 交易內載入訂單並萃取為純快照（明細於 session 內載入，避免交易外碰 lazy）
                Page<OrderView> orderViews = orderTransactionalService.loadOrderViews(accountId, pageable);

                // 2. 一次性批量查詢當頁所有商品（遠端呼叫在交易外）
                Set<Integer> allProductIds = orderViews.getContent().stream()
                                .flatMap(v -> v.items().stream())
                                .map(OrderItemRequest::productId)
                                .collect(Collectors.toSet());
                Map<Integer, GetProductDetailResponse> productMap = allProductIds.isEmpty()
                                ? Collections.emptyMap()
                                : batchGetProductDetails(allProductIds);

                // 3. 計算每個訂單的總金額並轉換為 DTO
                Page<GetOrderListResponse> responsePage = orderViews.map(v -> GetOrderListResponse.builder()
                                .orderId(v.orderId())
                                .status(v.status())
                                .totalAmount(calculateTotalAmount(v.items(), productMap))
                                .build());

                return PageResponse.from(responsePage);
        }

        /**
         * @param orderId
         * @return GetOrderDetailResponse
         */
        @Bulkhead(name = "order-read")
        @RateLimiter(name = "order-read")
        public GetOrderDetailResponse getOrderDetailByOrderId(Integer orderId) {
                // 1. 交易內載入訂單與明細，萃取為純快照（找不到直接噴 404）
                OrderView view = orderTransactionalService.loadOrderView(orderId);

                // 2. 批量獲取商品資訊（先收集 ID 再一次查詢，避免 N+1；遠端呼叫在交易外）
                Set<Integer> productIds = view.items().stream()
                                .map(OrderItemRequest::productId)
                                .collect(Collectors.toSet());
                Map<Integer, GetProductDetailResponse> productMap = batchGetProductDetails(productIds);

                // 3. 組裝明細 DTO
                List<OrderItemDTO> itemDTOs = view.items().stream()
                                .map(item -> {
                                        GetProductDetailResponse product = productMap.get(item.productId());
                                        return OrderItemDTO.builder()
                                                        .productId(item.productId())
                                                        .productName(product.name())
                                                        .quantity(item.quantity())
                                                        .productPrice(product.price())
                                                        .build();
                                })
                                .collect(Collectors.toList());

                // 4. 回傳結果（總金額沿用同一份 productMap，不重複遠端查詢）
                return GetOrderDetailResponse.builder()
                                .accountId(view.accountId())
                                .orderStatus(view.status())
                                .totalAmount(calculateTotalAmount(view.items(), productMap))
                                .items(itemDTOs)
                                .build();
        }

        /**
         * @param updateOrderRequest
         * @return UpdateOrderResponse
         */
        @Bulkhead(name = "order-write")
        @RateLimiter(name = "order-write")
        public void updateOrder(UpdateOrderRequest request) {
                ServiceValidator.validateNotNull(request, "Update order request");
                ServiceValidator.validateNotNull(request.orderId(), "Update order id");
                ServiceValidator.validateNotNull(request.orderStatus(), "Update order status");
                ServiceValidator.validateNotEmpty(request.items(), "Update order items");
                // 1. 先驗證請求本身（同一商品只能一筆），失敗即擋下，不必查 DB 或動庫存
                Set<OrderItemRequest> uniqueItems = validateAndConvertToUniqueItems(
                                request.items(),
                                detail -> detail.productId(),
                                detail -> detail.quantity());

                // 2. 交易內載入現有訂單快照（取得原明細與帳戶，避免交易外碰 lazy）
                OrderView view = orderTransactionalService.loadOrderView(request.orderId());
                Set<OrderItemRequest> originalItems = Set.copyOf(view.items());

                productClient.adjustStock(AdjustStockRequest.builder()
                                .from(originalItems)
                                .to(uniqueItems)
                                .build());

                // 將資料庫操作委派給交易服務（交易內自行載入 managed 實體），失敗則補償反轉庫存操作
                try {
                        orderTransactionalService.updateOrder(request);
                } catch (Exception e) {
                        // 補償: 將庫存調整回原狀 (from 與 to 互換)
                        try {
                                productClient.adjustStock(AdjustStockRequest.builder()
                                                .from(uniqueItems)
                                                .to(originalItems)
                                                .build());
                        } catch (Exception compensationEx) {
                                log.error("更新訂單失敗後，補償反轉庫存也失敗，需人工介入處理。" +
                                                "訂單ID: {}, 帳戶ID: {}, 原商品清單: {}, 新商品清單: {}, 原始異常: {}, 補償異常: {}",
                                                request.orderId(),
                                                view.accountId(),
                                                originalItems.stream()
                                                                .map(item -> String.format("商品%d(數量%d)",
                                                                                item.productId(), item.quantity()))
                                                                .collect(Collectors.joining(", ")),
                                                uniqueItems.stream()
                                                                .map(item -> String.format("商品%d(數量%d)",
                                                                                item.productId(), item.quantity()))
                                                                .collect(Collectors.joining(", ")),
                                                e.getMessage(),
                                                compensationEx.getMessage(),
                                                compensationEx);
                        }
                        throw e;
                }
        }

        /**
         * @param orderId
         */
        @Bulkhead(name = "order-write")
        @RateLimiter(name = "order-write")
        public void deleteOrder(Integer orderId) {
                ServiceValidator.validateNotNull(orderId, "Order ID");

                // 1. 交易內載入並驗證狀態，把後續所需資料萃取為純 DTO（避免 lazy 洩漏到交易外、不依賴 OSIV）
                OrderDeletionPlan plan = orderTransactionalService.prepareOrderDeletion(orderId);

                // 2. 先歸還庫存（外部服務調用放在前面且在交易外，失敗時訂單不受影響）
                productClient.releaseStock(plan.items());

                // 3. 再刪除訂單，若失敗則補償重新扣回庫存
                try {
                        orderTransactionalService.deleteOrder(orderId, plan.version());
                } catch (Exception e) {
                        // 補償: 重新預留已歸還的庫存
                        try {
                                productClient.reserveStock(plan.items());
                        } catch (Exception compensationEx) {
                                log.error("刪除訂單失敗後，補償重新扣回庫存也失敗，需人工介入處理。" +
                                                "訂單ID: {}, 帳戶ID: {}, 商品清單: {}, 原始異常: {}, 補償異常: {}",
                                                orderId,
                                                plan.accountId(),
                                                plan.items().stream()
                                                                .map(item -> String.format("商品%d(數量%d)",
                                                                                item.productId(), item.quantity()))
                                                                .collect(Collectors.joining(", ")),
                                                e.getMessage(),
                                                compensationEx.getMessage(),
                                                compensationEx);
                        }
                        throw e;
                }
        }

        /**
         * @param productIds
         * @return Map<Integer, GetProductDetailResponse>
         */
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
                ServiceValidator.validateNotNull(accountId, "Account ID");
                return !orderInfoRepository.findByAccountId(accountId).isEmpty();
        }

}
