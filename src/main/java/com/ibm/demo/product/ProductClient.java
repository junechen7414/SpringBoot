package com.ibm.demo.product;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.util.ProcessOrderItemsRequest;

@HttpExchange("/product")
public interface ProductClient {

    /**
     * 獲取商品詳細資訊
     * Spring 會自動將 Set<Integer> 轉換為多個 ids 查詢參數
     */
    @GetExchange("/batch")
    List<GetProductDetailResponse> getProductDetails(@RequestParam("ids") Set<Integer> ids);

    /**
     * 處理訂單商品庫存
     */
    @PostExchange("/processOrderItems")
    void processOrderItems(@RequestBody ProcessOrderItemsRequest request);
}