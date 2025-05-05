package com.ibm.demo.product;

import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductInactiveException;
import com.ibm.demo.product.DTO.GetProductDetailResponse;

@Component
public class ProductClient {
    private final WebClient webClient;

    public ProductClient(@Qualifier("productWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Map<Integer, GetProductDetailResponse> getProductDetails(Set<Integer> ids) {
        // Define the type for the response Map<Integer, GetProductDetailResponse>
        ParameterizedTypeReference<Map<Integer, GetProductDetailResponse>> mapType = new ParameterizedTypeReference<>() {
        };

        // Perform the GET request
        try {
            // Perform the GET request
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/getDetails") // Endpoint path in ProductController
                            // Add each ID as a separate 'ids' query parameter
                            .queryParam("ids", ids)
                            .build())
                    .retrieve() // Retrieve the response body
                    .bodyToMono(mapType) // Convert the response body to a Mono<Map<Integer, GetProductDetailResponse>>
                    .block(); // Block until the response is received (synchronous call)
        } catch (WebClientResponseException ex) {
            String errorMessage = extractErrorMessage(ex, "無法獲取商品詳細資訊");
            if (ex.getStatusCode() == HttpStatusCode.valueOf(404)) {
                // 商品服務的 404 意味著有商品 ID 找不到
                throw new ResourceNotFoundException(errorMessage);
            } else if (ex.getStatusCode() == HttpStatusCode.valueOf(400)) {
                // 商品服務的 400 表示商品不可銷售
                throw new ProductInactiveException(errorMessage);
            }
            throw new RuntimeException("呼叫商品服務失敗: " + errorMessage, ex);
        }
    }

    /**
     * 批量更新商品庫存
     *
     * @param stockUpdates Map<商品ID, 新庫存數量>
     */
    public void updateProductsStock(Map<Integer, Integer> stockUpdates) {
        try {
            webClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/batchUpdateStockQuantity")
                            .build())
                    .bodyValue(stockUpdates)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException ex) {
            String errorMessage = extractErrorMessage(ex, "更新商品庫存失敗");
            if (ex.getStatusCode() == HttpStatusCode.valueOf(404)) {
                // 如果更新時發現商品不存在
                throw new ResourceNotFoundException(errorMessage);
            } else if (ex.getStatusCode() == HttpStatusCode.valueOf(400)) {
                // 可能是請求格式錯誤或其他業務驗證失敗
                throw new InvalidRequestException(errorMessage);
            }
            throw new RuntimeException("呼叫商品服務更新庫存失敗: " + errorMessage, ex);
        }
    }

    // 輔助方法，嘗試從 WebClientResponseException 中提取下游服務的錯誤訊息
    // 從 API 呼叫失敗的例外中，優先取得對方服務回傳的具體錯誤訊息 (假設格式符合 ApiErrorResponse)。如果無法取得，就退一步使用 HTTP
    // 狀態文字。如果連 HTTP 狀態文字都怪怪的或不適用，最後就使用一個預設的通用錯誤訊息。
    private String extractErrorMessage(WebClientResponseException ex, String defaultMessage) {
        try {
            ApiErrorResponse errorResponse = ex.getResponseBodyAs(ApiErrorResponse.class);
            if (errorResponse != null && errorResponse.getMessage() != null && !errorResponse.getMessage().isEmpty()) {
                return errorResponse.getMessage();
            }
        } catch (Exception parseEx) {
            return ex.getStatusText() + " (無法解析詳細錯誤)";
        }
        return defaultMessage;
    }
}
