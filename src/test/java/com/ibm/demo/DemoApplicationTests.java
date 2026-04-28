package com.ibm.demo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("SanityTest")
@SpringBootTest
@ActiveProfiles("test") // 使用 test profile，確保使用測試專用的資料庫設定
class DemoApplicationTests {

	@Test
	void contextLoads() {
		// This test is intentionally left empty.
		// It will pass if the application context loads successfully.
		// sanity check

		// 不開啟容器會導致 @Autowired 失敗
		// @Autowired private DemoApplication demoApplication;
		// demoApplication.main(new String[] {});		

		// 但是其他單元測試不會有影響，因為不透過DemoApplication，使用Mock的話不會有問題
		
	}

}
