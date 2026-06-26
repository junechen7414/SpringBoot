package com.ibm.demo.product;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.internal.AdjustStockRequest;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;

@HttpExchange("/product")
public interface ProductClient {

    /**
     * 獲取商品詳細資訊
     * Spring 會自動將 Set<Integer> 轉換為多個 ids 查詢參數
     */
    @GetExchange("/batch")
    List<GetProductDetailResponse> getProductDetails(@RequestParam("ids") Set<Integer> ids);

    /**
     * 預留庫存（建立訂單）
     */
    @PostExchange("/reserve")
    void reserveStock(@RequestBody Set<OrderItemRequest> items);

    /**
     * 釋放庫存（刪除訂單）
     */
    @PostExchange("/release")
    void releaseStock(@RequestBody Set<OrderItemRequest> items);

    /**
     * 調整庫存（更新訂單，依新舊差值處理）
     */
    @PostExchange("/adjustStock")
    void adjustStock(@RequestBody AdjustStockRequest request);
}