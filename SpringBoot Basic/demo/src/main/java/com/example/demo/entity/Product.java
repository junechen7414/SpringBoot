package com.example.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import io.swagger.v3.oas.annotations.media.Schema; // Import Swagger schema annotation


@Schema(description = "商品資料模型") // Add Swagger schema description
@Entity
@Table(name = "PRODUCTS") // Renamed table
public class Product { // Renamed class

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "自動生成的商品ID")
    private Long id;

    @Schema(description = "商品名稱")
    private String name;

    @Schema(description = "商品價格")
    private Integer price; // Renamed field from value to price

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPrice() { // Renamed getter
        return price;
    }

    public void setPrice(Integer price) { // Renamed setter
        this.price = price;
    }
}
