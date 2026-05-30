package com.ibm.demo;

import java.time.Duration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
public abstract class BaseIntegrationTest {

    private static final Duration CONTAINER_STARTUP_TIMEOUT = Duration.ofMinutes(10);

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:slim-faststart")
            .withStartupTimeout(CONTAINER_STARTUP_TIMEOUT)
            .waitingFor(
                Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1)
                    .withStartupTimeout(CONTAINER_STARTUP_TIMEOUT)
            );
}
