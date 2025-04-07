package com.example.jpa;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JpaApplication implements CommandLineRunner {

	@Autowired
	private TestService testService;

	public static void main(String[] args) {
		SpringApplication.run(JpaApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("--- Spring Boot JPA 演示應用程式啟動 ---");

		// 呼叫 Service 層的方法來執行 JPA 操作
		testService.performJpaOperations();

		// 可以選擇性地保留或移除原始的 testConnection 呼叫
		// System.out.println("\n--- 執行額外的連線測試 ---");
		// testService.testConnection();

		System.out.println("\n--- 應用程式執行完畢 ---");
	}
}
