package com.example.demo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "商品傳輸資料模型")
public class Product_DTO {
    @Schema(description = "商品名稱", example = "Bpple")
    private String name;
    @Schema(description = "商品價格", example = "100")
    private Integer price;

    //constructor
    public Product_DTO() {
    }

    public Product_DTO(String name, Integer price) {
        this.name = name;
        this.price = price;
    }

    //getter and setter
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }
}
