package com.ibm.demo.product;

import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getDetails") // Endpoint path in ProductController
                        // Add each ID as a separate 'ids' query parameter
                        .queryParam("ids", ids)
                        .build())
                .retrieve() // Retrieve the response body                
                .bodyToMono(mapType) // Convert the response body to a Mono<Map<Integer, GetProductDetailResponse>>
                .block(); // Block until the response is received (synchronous call)       
    }
}
