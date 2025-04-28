package com.ibm.demo.product;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;


@Component
public class ProductClient {
    private final WebClient webClient;

    public ProductClient(@Qualifier("productWebClient") WebClient webClient){
        this.webClient = webClient;
    }
}
