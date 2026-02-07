package com.ibm.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
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
