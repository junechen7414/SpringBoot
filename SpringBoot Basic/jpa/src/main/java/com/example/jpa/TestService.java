package com.example.jpa;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestService {

    @Autowired
    private TestRepository testRepository;

    @Transactional
    public void testConnection() {
        try {
            TestEntity entity = new TestEntity();
            entity.setName("Test Name");
            testRepository.save(entity);
            System.out.println("Successfully saved test entity with ID: " + entity.getId());
            // 可以在這裡加入讀取或刪除操作來進一步測試
            // TestEntity savedEntity = testRepository.findById(entity.getId()).orElse(null);
            // if (savedEntity != null) {
            //     System.out.println("Successfully retrieved test entity: " + savedEntity.getName());
            // }
            // testRepository.delete(entity);
            // System.out.println("Successfully deleted test entity.");
            System.out.println("Database connection test successful!");
        } catch (Exception e) {
            System.err.println("Database connection test failed!");
            e.printStackTrace();
        }
    }
}
