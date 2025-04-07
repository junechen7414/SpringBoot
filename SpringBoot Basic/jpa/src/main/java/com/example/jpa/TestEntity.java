package com.example.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "TEST_TABLE") // 您可以根據需要更改表格名稱
public class TestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 或其他適合您資料庫的生成策略
    private Long id;

    private String name;

    private Integer value; // 新增 value 欄位

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

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
