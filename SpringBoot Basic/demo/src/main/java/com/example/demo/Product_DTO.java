package com.example.demo;

public class Product_DTO {
    private String name;
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
