package com.example.demo.repository;

import com.example.demo.entity.Product; // Updated import path for Product
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> { // Renamed interface and updated generic type

    // 1. JPA Query Method: 根據 name 欄位查詢 (忽略大小寫)
    Optional<Product> findByNameIgnoreCase(String name);

    // 2. JPQL: 使用 @Query 註解和 JPQL 語法查詢
    @Query("SELECT p FROM Product p WHERE p.name = :name") // Updated JPQL to use Product
    List<Product> findByNameWithJpql(@Param("name") String name);

    // 3. Native SQL: 使用 @Query 註解和原生 SQL 語法查詢
    @Query(value = "SELECT * FROM PRODUCTS WHERE name = :name", nativeQuery = true) // Updated Native SQL for table name
    List<Product> findByNameWithNativeSql(@Param("name") String name);

    // --- 以下為 ProductService 中可能使用的方法 ---

    // JPA Query Method: 查詢名稱包含特定字串的 Entity (忽略大小寫)
    List<Product> findByNameContainingIgnoreCase(String namePart);

    // JPA Query Method: 查詢名稱包含特定字串的 Entity (區分大小寫)
    List<Product> findByNameContaining(String namePart);

    // JPQL: 查詢 price 大於指定值的 Entity
    @Query("SELECT p FROM Product p WHERE p.price > :minPrice") // Updated JPQL for field name and parameter name
    List<Product> findByPriceGreaterThanJpql(@Param("minPrice") Integer price); // Renamed method and parameter

    // Native SQL: 根據名稱查詢
    @Query(value = "SELECT * FROM PRODUCTS WHERE name = :exactName", nativeQuery = true) // Updated Native SQL for table name
    List<Product> findByNameNative(@Param("exactName") String name);


    // --- 繼承自 JpaRepository 的方法範例 (無需自行定義) ---
    // save(Product entity): 新增或更新 Entity
    // findById(Long id): 根據 ID 查詢
    // findAll(): 查詢所有
    // deleteById(Long id): 根據 ID 刪除
}
