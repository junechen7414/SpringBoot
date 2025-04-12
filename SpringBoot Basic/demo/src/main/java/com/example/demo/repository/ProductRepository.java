package com.example.demo.repository;

import com.example.demo.entity.Product; // Updated import path for Product
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> { // Renamed interface and updated generic type

    // --- 自訂查詢方法範例：使用三種不同方式實現相同功能 (根據名稱精確查詢) ---

    // 1. JPA Query Method: 根據方法命名規則自動生成查詢
    // 根據 name 欄位查詢 (區分大小寫)
    List<Product> findByName(String name);

    // 2. JPQL: 使用 @Query 註解和 JPQL (Java Persistence Query Language) 語法查詢
    // 操作 Entity 物件和屬性，非資料庫表格和欄位
    @Query("SELECT p FROM Product p WHERE p.name = :name")
    List<Product> findByNameWithJpql(@Param("name") String name);

    // 3. Native SQL: 使用 @Query 註解和原生 SQL 語法查詢
    // 直接編寫特定資料庫的 SQL，彈性最大但無資料庫獨立性
    @Query(value = "SELECT * FROM PRODUCTS WHERE name = :name", nativeQuery = true)
    List<Product> findByNameWithNativeSql(@Param("name") String name);

    // --- 繼承自 JpaRepository 的方法範例 (無需自行定義，常見方法列舉) ---
    // save(Product entity): 新增或更新 Entity
    // findById(Long id): 根據 ID 查詢
    // findAll(): 查詢所有
    // deleteById(Long id): 根據 ID 刪除
}
