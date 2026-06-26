package com.ibm.demo;

import java.time.Duration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest
@ActiveProfiles("integration-test")
public abstract class BaseIntegrationTest {

    private static final Duration CONTAINER_STARTUP_TIMEOUT = Duration.ofMinutes(10);

    // Singleton container pattern: 整個 JVM 只啟動一次，所有整合測試類別共用同一個容器。
    // 這裡刻意「不」使用 @Testcontainers / @Container —— 該生命週期會在第一個測試類別結束後
    // 停掉容器，但 Spring ApplicationContext 會被快取並跨類別重用，重用的 context 仍指向那個
    // 已被停掉的容器 port，導致後續整合測試連線到死掉的 listener (ORA-12541)。
    // 改為手動 start() 讓容器存活整個測試流程；Ryuk 會在 JVM 結束時回收它。
    @ServiceConnection
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:slim-faststart")
            .withStartupTimeout(CONTAINER_STARTUP_TIMEOUT);

    static {
        oracle.start();
    }
}
