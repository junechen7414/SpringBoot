package com.example.jpa;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TestService {

    @Autowired
    private TestRepository testRepository; // 注入 Repository

    /**
     * 演示 JPA 的基本 CRUD 操作、查詢方法、Stream API 和 JPQL/原生 SQL。
     */
    @Transactional // 確保所有操作在同一個事務中執行
    public void performJpaOperations() {
        System.out.println("\n--- 開始 JPA 操作演示 ---");

        // 1. 新增 (Create) - 使用 save()
        System.out.println("\n[1] 新增資料...");
        TestEntity entity1 = new TestEntity();
        entity1.setName("Alice");
        entity1.setValue(100);
        testRepository.save(entity1); // 保存 Entity 到資料庫

        TestEntity entity2 = new TestEntity();
        entity2.setName("Bob");
        entity2.setValue(200);
        testRepository.save(entity2);

        TestEntity entity3 = new TestEntity();
        entity3.setName("Charlie");
        entity3.setValue(150);
        testRepository.save(entity3);
        System.out.println("新增了三筆資料: Alice, Bob, Charlie");

        // 2. 查詢 (Read)
        System.out.println("\n[2] 查詢資料...");

        // 2.1 查詢所有資料 + Stream API & Lambda
        System.out.println("  查詢所有資料並使用 Stream API 篩選 value > 120:");
        List<TestEntity> allEntities = testRepository.findAll(); // JPA 內建方法
        List<String> namesWithValueGreaterThan120 = allEntities.stream() // 轉換為 Stream
                // 修改 filter: 增加 null 檢查
                .filter(e -> e.getValue() != null && e.getValue() > 120) // Lambda: 篩選 value 不是 null 且 > 120
                .map(TestEntity::getName) // Lambda: 提取 name 屬性
                .collect(Collectors.toList()); // 收集結果為 List
        System.out.println("    結果: " + namesWithValueGreaterThan120);

        // 2.2 根據 ID 查詢
        System.out.println("  根據 ID 查詢 (ID=" + entity1.getId() + "):");
        Optional<TestEntity> foundById = testRepository.findById(entity1.getId()); // JPA 內建方法
        foundById.ifPresent(e -> System.out.println("    找到: " + e.getName() + ", Value: " + e.getValue()));

        // 2.3 使用自訂 JPA Query Method 查詢 (名稱包含 'li')
        System.out.println("  使用自訂查詢方法 findByNameContaining('li'):");
        List<TestEntity> foundByName = testRepository.findByNameContaining("li"); // 自訂查詢方法
        foundByName.forEach(e -> System.out.println("    找到: " + e.getName()));

        // 2.4 使用 JPQL 查詢 (value > 160)
        System.out.println("  使用 JPQL 查詢 (value > 160):");
        List<TestEntity> foundByJpql = testRepository.findByValueGreaterThanJpql(160); // 使用 @Query 定義的 JPQL
        foundByJpql.forEach(e -> System.out.println("    找到: " + e.getName() + ", Value: " + e.getValue()));

        // 2.5 使用原生 SQL 查詢 (名稱為 'Bob')
        System.out.println("  使用原生 SQL 查詢 (name = 'Bob'):");
        List<TestEntity> foundByNativeSql = testRepository.findByNameNative("Bob"); // 使用 @Query 定義的原生 SQL
        foundByNativeSql.forEach(e -> System.out.println("    找到: " + e.getName() + ", Value: " + e.getValue()));


        // 3. 更新 (Update) - 先查詢再修改，然後 save()
        System.out.println("\n[3] 更新資料...");
        System.out.println("  更新 Bob 的 value 為 250:");
        Optional<TestEntity> bobOptional = testRepository.findByNameNative("Bob").stream().findFirst();
        bobOptional.ifPresent(bob -> {
            System.out.println("    更新前: " + bob.getName() + ", Value: " + bob.getValue());
            bob.setValue(250); // 修改 Entity 的屬性
            testRepository.save(bob); // 重新保存，JPA 會執行更新操作
            System.out.println("    更新後: " + bob.getName() + ", Value: " + bob.getValue());
        });

        // 4. 刪除 (Delete)
        System.out.println("\n[4] 刪除資料...");
        System.out.println("  刪除 Charlie (ID=" + entity3.getId() + "):");
        testRepository.deleteById(entity3.getId()); // JPA 內建方法
        // 驗證是否刪除
        Optional<TestEntity> deletedEntity = testRepository.findById(entity3.getId());
        if (deletedEntity.isEmpty()) {
            System.out.println("    Charlie 已成功刪除。");
        } else {
            System.out.println("    刪除 Charlie 失敗。");
        }

        System.out.println("\n--- JPA 操作演示結束 ---");
    }

    /**
     * 原始的連線測試方法，現在已整合到 performJpaOperations 中。
     * 可以保留或移除。
     */
    @Transactional
    public void testConnection() {
        try {
            // 簡單測試: 嘗試計算數量
            long count = testRepository.count();
            System.out.println("Database connection test successful! Current entity count: " + count);
        } catch (Exception e) {
            System.err.println("Database connection test failed!");
            e.printStackTrace();
        }
        System.out.println("Database connection test finished.");
    }
}
