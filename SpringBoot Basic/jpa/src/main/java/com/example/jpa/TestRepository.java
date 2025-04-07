package com.example.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestRepository extends JpaRepository<TestEntity, Long> {

    // 1. JPA Query Method: 根據 name 欄位查詢 (忽略大小寫)
    Optional<TestEntity> findByNameIgnoreCase(String name);

    // 2. JPQL: 使用 @Query 註解和 JPQL 語法查詢
    @Query("SELECT t FROM TestEntity t WHERE t.name = :name")
    List<TestEntity> findByNameWithJpql(@Param("name") String name);

    // 3. Native SQL: 使用 @Query 註解和原生 SQL 語法查詢
    @Query(value = "SELECT * FROM TEST_TABLE WHERE name = :name", nativeQuery = true)
    List<TestEntity> findByNameWithNativeSql(@Param("name") String name);

    // --- 以下為 TestService 中使用的方法 ---

    // JPA Query Method: 查詢名稱包含特定字串的 Entity (忽略大小寫)
    List<TestEntity> findByNameContainingIgnoreCase(String namePart);

    // JPA Query Method: 查詢名稱包含特定字串的 Entity (區分大小寫)
    List<TestEntity> findByNameContaining(String namePart); // TestService 使用此方法

    // JPQL: 查詢 value 大於指定值的 Entity
    @Query("SELECT t FROM TestEntity t WHERE t.value > :minValue")
    List<TestEntity> findByValueGreaterThanJpql(@Param("minValue") Integer value); // TestService 使用此方法

    // Native SQL: 根據名稱查詢 (與 findByNameWithNativeSql 類似，但這是 TestService 實際呼叫的名稱)
    @Query(value = "SELECT * FROM test_table WHERE name = :exactName", nativeQuery = true)
    List<TestEntity> findByNameNative(@Param("exactName") String name); // TestService 使用此方法


    // --- 繼承自 JpaRepository 的方法範例 (無需自行定義) ---
    // save(TestEntity entity): 新增或更新 Entity
    // findById(Long id): 根據 ID 查詢
    // findAll(): 查詢所有
    // deleteById(Long id): 根據 ID 刪除
}
