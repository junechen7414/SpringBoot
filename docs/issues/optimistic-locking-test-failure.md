# OptimisticLockingIntegrationTest Failure Analysis

**Issue ID**: `optimistic-locking-test-failure`  
**Created**: 2026-06-02  
**Status**: 🔴 Open  
**Severity**: High  
**Category**: Integration Testing / Database Connectivity

---

## 📋 Table of Contents

1. [Problem Overview](#problem-overview)
2. [Failed Tests](#failed-tests)
3. [Error Stack Trace](#error-stack-trace)
4. [Technical Background](#technical-background)
5. [Why Oracle Database is Required](#why-oracle-database-is-required)
6. [Clarification: Not Related to Resilience4j](#clarification-not-related-to-resilience4j)
7. [Solution Approaches](#solution-approaches)
8. [Reproduction Steps](#reproduction-steps)
9. [Related Files](#related-files)
10. [Best Practices](#best-practices)
11. [FAQ](#faq)

---

## 🔍 Problem Overview

The `OptimisticLockingIntegrationTest` test suite is failing due to **Oracle database connection issues** when running integration tests. The tests are designed to verify optimistic locking mechanisms for concurrent data modifications across three entities: Product, Account, and OrderInfo.

### Key Facts

- **Test Class**: `OptimisticLockingIntegrationTest`
- **Test Type**: Integration Test (requires real database)
- **Database**: Oracle Database (via Testcontainers)
- **Root Cause**: Oracle database container not running or not accessible
- **Impact**: All 6 optimistic locking tests fail with connection errors

---

## ❌ Failed Tests

The following 6 tests are failing:

### 1. JPA Standard Update Tests (3 tests)

1. **`testUpdateProductOptimisticLocking`**
   - Display Name: "測試商品更新時的樂觀鎖機制 (JPA 標準)"
   - Purpose: Verify optimistic locking when updating Product entity

2. **`testUpdateAccountOptimisticLocking`**
   - Display Name: "測試帳戶更新時的樂觀鎖機制 (JPA 標準)"
   - Purpose: Verify optimistic locking when updating Account entity

3. **`testUpdateOrderOptimisticLocking`**
   - Display Name: "測試訂單更新時的樂觀鎖機制 (JPA 標準)"
   - Purpose: Verify optimistic locking when updating OrderInfo entity

### 2. Custom @Query Soft Delete Tests (3 tests)

4. **`testSoftDeleteProductOptimisticLocking`**
   - Display Name: "測試商品軟刪除時的樂觀鎖機制 (自定義 @Query)"
   - Purpose: Verify optimistic locking in custom soft delete query for Product

5. **`testSoftDeleteAccountOptimisticLocking`**
   - Display Name: "測試帳戶軟刪除時的樂觀鎖機制 (自定義 @Query)"
   - Purpose: Verify optimistic locking in custom soft delete query for Account

6. **`testSoftDeleteOrderOptimisticLocking`**
   - Display Name: "測試訂單軟刪除時的樂觀鎖機制 (自定義 @Query)"
   - Purpose: Verify optimistic locking in custom soft delete query for OrderInfo

---

## 📊 Error Stack Trace

```
OptimisticLockingIntegrationTest > 測試帳戶軟刪除時的樂觀鎖機制 (自定義 @Query) FAILED
    org.springframework.transaction.CannotCreateTransactionException at JpaTransactionManager.java:467
        Caused by: org.hibernate.exception.JDBCConnectionException at SQLExceptionTypeDelegate.java:51
            Caused by: java.sql.SQLTransientConnectionException at HikariPool.java:714
                Caused by: java.sql.SQLException at T4CConnection.java:853
                    Caused by: oracle.net.ns.NetException at TcpNTAdapter.java:418
                        Caused by: java.net.ConnectException at Net.java:-2
```

### Error Analysis

| Layer | Exception | Meaning |
|-------|-----------|---------|
| **Transaction Management** | `CannotCreateTransactionException` | Spring cannot start a database transaction |
| **Hibernate** | `JDBCConnectionException` | Hibernate cannot establish JDBC connection |
| **Connection Pool** | `SQLTransientConnectionException` | HikariCP cannot get a connection from pool |
| **Oracle Driver** | `SQLException` | Oracle JDBC driver connection failure |
| **Network** | `NetException` → `ConnectException` | TCP connection to Oracle database failed |

**Root Cause**: The Oracle database is not running or not accessible at the expected network address.

---

## 🏗️ Technical Background

### Test Architecture

The `OptimisticLockingIntegrationTest` extends `BaseIntegrationTest`, which provides:

```java
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
public abstract class BaseIntegrationTest {
    
    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(10))
            .waitingFor(
                Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(10))
            );
}
```

### Key Components

1. **Testcontainers**: Automatically starts Oracle database in Docker container
2. **@ServiceConnection**: Auto-configures Spring datasource from container
3. **Oracle Free Edition**: Uses `gvenzl/oracle-free:slim-faststart` image
4. **Startup Timeout**: 10 minutes to allow Oracle initialization

### Optimistic Locking Mechanism

The tests verify two types of optimistic locking:

#### 1. JPA Standard Optimistic Locking

```java
@Version
private Integer version;
```

- Uses `@Version` annotation on entity
- JPA automatically checks version on update
- Throws `ObjectOptimisticLockingFailureException` on conflict

#### 2. Custom @Query Optimistic Locking

```java
@Modifying
@Query("UPDATE Product p SET p.deleted = true, p.version = p.version + 1 
        WHERE p.id = :id AND p.version = :version")
int softDeleteById(@Param("id") Integer id, @Param("version") Integer version);
```

- Custom JPQL query includes version check
- Returns 0 if version mismatch (no rows updated)
- Application code must handle the return value

### Test Strategy

Each test simulates concurrent modification:

1. Create entity with version 0
2. Load entity twice (entity1 and entity2)
3. Detach entity2 from persistence context
4. Update entity1 (version becomes 1)
5. Attempt to update entity2 with old version 0
6. Verify optimistic locking failure

---

## 🗄️ Why Oracle Database is Required

### Cannot Use H2 Database

While H2 is commonly used for testing, these integration tests **require Oracle** for the following reasons:

#### 1. Production Parity

```yaml
# application-dev.yml (Production-like environment)
spring:
  datasource:
    url: jdbc:oracle:thin:@oracle-db:1521/FREEPDB1
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.OracleDialect
```

The application uses Oracle in production, so integration tests must use Oracle to ensure:
- SQL dialect compatibility
- Transaction behavior consistency
- Locking mechanism accuracy

#### 2. Dialect-Specific Behavior

Oracle and H2 have different:
- **SQL Syntax**: Oracle uses `DUAL` table, different date functions
- **Transaction Isolation**: Different default isolation levels
- **Locking Mechanisms**: Oracle's row-level locking differs from H2
- **Sequence Generation**: Oracle sequences vs H2 auto-increment

#### 3. Flyway Migrations

```yaml
# application-integration-test.yml
spring:
  flyway:
    enabled: true
```

The project uses Flyway migrations written for Oracle:
- Oracle-specific DDL syntax
- Oracle data types (VARCHAR2, NUMBER, etc.)
- Oracle constraints and indexes

#### 4. Real Integration Testing

Integration tests should test the **actual integration** with production-like infrastructure, not mocked or simplified versions.

---

## ⚠️ Clarification: Not Related to Resilience4j

### Common Misconception

The test failures are **NOT** caused by Resilience4j configuration, despite the presence of extensive Resilience4j settings in `application.yml`.

### Why Resilience4j is Not the Cause

1. **Resilience4j is for Runtime Protection**
   ```yaml
   resilience4j:
     bulkhead:
       instances:
         account-write:
           max-concurrent-calls: 100
   ```
   - Resilience4j protects against overload during **runtime**
   - Integration tests run in **test context** with minimal load
   - No concurrent requests to trigger bulkhead/rate limiter

2. **Error Occurs Before Application Logic**
   ```
   CannotCreateTransactionException at JpaTransactionManager.java:467
   ```
   - Error happens at **transaction creation** (infrastructure layer)
   - Resilience4j operates at **service method** level (application layer)
   - Database connection fails before any service method is invoked

3. **Test Profile Doesn't Override Resilience4j**
   ```yaml
   # application-integration-test.yml
   spring:
     jpa:
       hibernate:
         ddl-auto: validate
   ```
   - Test profile only configures JPA and Flyway
   - Resilience4j configuration remains active but unused
   - No Resilience4j-related errors in stack trace

### Actual Root Cause

The failure is purely **infrastructure-related**:
- Oracle database container not started
- Docker/Podman not running
- Network connectivity issues
- Insufficient system resources for container

---

## 🔧 Solution Approaches

### Solution 1: Start Oracle Database Container (Recommended)

This is the **correct solution** for running integration tests.

#### Prerequisites

- Docker or Podman installed and running
- Sufficient system resources (4GB+ RAM recommended)
- Network connectivity for pulling Docker images

#### Steps

1. **Verify Docker/Podman is Running**

   ```bash
   # For Docker
   docker ps
   
   # For Podman
   podman ps
   ```

2. **Run Integration Tests**

   ```bash
   # Testcontainers will automatically:
   # 1. Pull gvenzl/oracle-free:slim-faststart image
   # 2. Start Oracle container
   # 3. Wait for database to be ready
   # 4. Run tests
   # 5. Stop and remove container
   
   ./gradlew test --tests OptimisticLockingIntegrationTest
   ```

3. **Monitor Container Startup**

   ```bash
   # In another terminal, watch container logs
   docker logs -f <container-id>
   
   # Look for: "DATABASE IS READY TO USE!"
   ```

#### Expected Behavior

- First run: ~5-10 minutes (image download + Oracle initialization)
- Subsequent runs: ~2-3 minutes (Oracle initialization only)
- All 6 tests should pass

#### Troubleshooting

**Issue**: Container fails to start
```bash
# Check Docker daemon
sudo systemctl status docker

# Check available disk space
df -h

# Check available memory
free -h
```

**Issue**: Timeout waiting for database
```java
// Increase timeout in BaseIntegrationTest.java
private static final Duration CONTAINER_STARTUP_TIMEOUT = Duration.ofMinutes(15);
```

---

### Solution 2: Use Existing Oracle Database

If you have an Oracle database already running (e.g., via docker-compose), you can configure tests to use it.

#### Steps

1. **Start Oracle Database**

   ```bash
   # Using docker-compose
   docker-compose up -d oracle-db
   
   # Wait for healthy status
   docker-compose ps
   ```

2. **Create Test Profile Configuration**

   Create `src/test/resources/application-integration-test-external.yml`:

   ```yaml
   spring:
     datasource:
       url: jdbc:oracle:thin:@localhost:1521/FREEPDB1
       username: ${ORACLE_DEV_USERNAME}
       password: ${ORACLE_DEV_PASSWORD}
     jpa:
       hibernate:
         ddl-auto: create-drop  # Recreate schema for each test
     flyway:
       enabled: true
       clean-disabled: false
   ```

3. **Modify BaseIntegrationTest**

   ```java
   @SpringBootTest
   @ActiveProfiles("integration-test-external")
   public abstract class BaseIntegrationTest {
       // Remove @Testcontainers and @Container
       // Tests will use external database
   }
   ```

4. **Run Tests**

   ```bash
   ./gradlew test --tests OptimisticLockingIntegrationTest
   ```

#### Pros and Cons

✅ **Pros**:
- Faster test execution (no container startup)
- Useful for local development
- Can inspect database state after tests

❌ **Cons**:
- Requires manual database management
- Not suitable for CI/CD pipelines
- Risk of test data pollution
- Not truly isolated tests

---

### Solution 3: Skip Integration Tests Temporarily

If you need to build the project but cannot run integration tests, you can skip them temporarily.

#### Steps

1. **Skip All Tests**

   ```bash
   ./gradlew build -x test
   ```

2. **Skip Only Integration Tests**

   ```bash
   # Run only unit tests (tagged with @Tag("UnitTest"))
   ./gradlew test --tests "*Test" -Dtest.profile=unit-test
   ```

3. **Exclude Integration Test Class**

   ```bash
   ./gradlew test --tests "*" --tests "!OptimisticLockingIntegrationTest"
   ```

#### When to Use

- ⚠️ **Temporary workaround only**
- Building for quick local verification
- CI/CD pipeline issues (should be fixed, not skipped)
- Resource-constrained environments

#### Important Notes

- ❌ **Do NOT skip tests in production builds**
- ❌ **Do NOT commit code that skips tests**
- ✅ **Always run full test suite before merging**

---

### Solution 4: Use GitHub Actions / CI Pipeline

For automated testing in CI/CD environments.

#### GitHub Actions Example

Create `.github/workflows/integration-tests.yml`:

```yaml
name: Integration Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  integration-test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: ~/.gradle/caches
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}
    
    - name: Run Integration Tests
      run: ./gradlew test --tests OptimisticLockingIntegrationTest
      env:
        # Testcontainers will use GitHub Actions Docker
        TESTCONTAINERS_RYUK_DISABLED: false
    
    - name: Upload Test Results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: test-results
        path: build/reports/tests/
```

#### Benefits

- ✅ Automated testing on every commit
- ✅ Isolated environment for each test run
- ✅ No local setup required
- ✅ Test results archived and accessible

---

## 🔄 Reproduction Steps

### Reproduce the Failure

1. **Ensure Docker/Podman is NOT running**

   ```bash
   # Stop Docker
   sudo systemctl stop docker
   
   # Or stop Podman
   podman machine stop
   ```

2. **Run Integration Tests**

   ```bash
   ./gradlew test --tests OptimisticLockingIntegrationTest
   ```

3. **Observe Failure**

   ```
   OptimisticLockingIntegrationTest > 測試帳戶軟刪除時的樂觀鎖機制 FAILED
       org.springframework.transaction.CannotCreateTransactionException
           Caused by: java.net.ConnectException
   ```

### Reproduce the Success

1. **Start Docker/Podman**

   ```bash
   # Start Docker
   sudo systemctl start docker
   
   # Or start Podman
   podman machine start
   ```

2. **Run Integration Tests**

   ```bash
   ./gradlew test --tests OptimisticLockingIntegrationTest
   ```

3. **Observe Success**

   ```
   OptimisticLockingIntegrationTest > testUpdateProductOptimisticLocking PASSED
   OptimisticLockingIntegrationTest > testUpdateAccountOptimisticLocking PASSED
   OptimisticLockingIntegrationTest > testUpdateOrderOptimisticLocking PASSED
   OptimisticLockingIntegrationTest > testSoftDeleteProductOptimisticLocking PASSED
   OptimisticLockingIntegrationTest > testSoftDeleteAccountOptimisticLocking PASSED
   OptimisticLockingIntegrationTest > testSoftDeleteOrderOptimisticLocking PASSED
   
   BUILD SUCCESSFUL
   ```

---

## 📁 Related Files

### Test Files

- [`src/test/java/com/ibm/demo/OptimisticLockingIntegrationTest.java`](../../src/test/java/com/ibm/demo/OptimisticLockingIntegrationTest.java)
  - Main test class with 6 optimistic locking tests
  
- [`src/test/java/com/ibm/demo/BaseIntegrationTest.java`](../../src/test/java/com/ibm/demo/BaseIntegrationTest.java)
  - Base class providing Testcontainers Oracle setup

### Configuration Files

- [`src/test/resources/application-integration-test.yml`](../../src/test/resources/application-integration-test.yml)
  - Test profile configuration for integration tests
  
- [`src/main/resources/application.yml`](../../src/main/resources/application.yml)
  - Main application configuration (includes Resilience4j)
  
- [`docker-compose.yml`](../../docker-compose.yml)
  - Docker Compose setup for local development Oracle database

### Entity Files

- [`src/main/java/com/ibm/demo/product/Product.java`](../../src/main/java/com/ibm/demo/product/Product.java)
  - Product entity with `@Version` field
  
- [`src/main/java/com/ibm/demo/account/Account.java`](../../src/main/java/com/ibm/demo/account/Account.java)
  - Account entity with `@Version` field
  
- [`src/main/java/com/ibm/demo/order/Entity/OrderInfo.java`](../../src/main/java/com/ibm/demo/order/Entity/OrderInfo.java)
  - OrderInfo entity with `@Version` field

### Repository Files

- [`src/main/java/com/ibm/demo/product/ProductRepository.java`](../../src/main/java/com/ibm/demo/product/ProductRepository.java)
  - Contains `softDeleteById` custom query
  
- [`src/main/java/com/ibm/demo/account/AccountRepository.java`](../../src/main/java/com/ibm/demo/account/AccountRepository.java)
  - Contains `softDeleteById` custom query
  
- [`src/main/java/com/ibm/demo/order/Repository/OrderInfoRepository.java`](../../src/main/java/com/ibm/demo/order/Repository/OrderInfoRepository.java)
  - Contains `softDeleteById` custom query

### Documentation

- [`docs/testing-architecture-overview.md`](../testing-architecture-overview.md)
  - Overview of testing strategy and architecture
  
- [`docs/test-data-initialization-guide.md`](../test-data-initialization-guide.md)
  - Guide for test data setup

---

## ✅ Best Practices

### 1. Integration Test Isolation

```java
@Transactional  // Each test runs in a transaction
public void testSoftDeleteAccountOptimisticLocking() {
    // Test code
    // Transaction rolls back after test
}
```

**Benefits**:
- Tests don't affect each other
- Database state is clean for each test
- No manual cleanup required

### 2. Use Testcontainers for True Integration

```java
@Container
@ServiceConnection
static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:slim-faststart");
```

**Benefits**:
- Tests run against real database
- No mocking of database behavior
- Catches database-specific issues
- Works in any environment (local, CI/CD)

### 3. Proper Version Field Usage

```java
@Version
@Column(name = "VERSION", nullable = false)
private Integer version;
```

**Best Practices**:
- Always use `Integer` or `Long` (not `int`/`long`)
- Make it `nullable = false` in database
- Never manually set version in application code
- Let JPA manage version automatically

### 4. Custom Query Optimistic Locking

```java
@Modifying
@Query("UPDATE Product p SET p.deleted = true, p.version = p.version + 1 
        WHERE p.id = :id AND p.version = :version")
int softDeleteById(@Param("id") Integer id, @Param("version") Integer version);
```

**Best Practices**:
- Always include version in WHERE clause
- Increment version in SET clause
- Return affected row count
- Check return value in service layer

### 5. Test Naming Conventions

```java
@Test
@DisplayName("測試商品軟刪除時的樂觀鎖機制 (自定義 @Query)")
public void testSoftDeleteProductOptimisticLocking() {
    // Test implementation
}
```

**Best Practices**:
- Use descriptive test method names
- Add `@DisplayName` for better test reports
- Group related tests in same class
- Use `@Tag` for test categorization

---

## ❓ FAQ

### Q1: Why do tests take so long to run?

**A**: First-time execution includes:
1. Downloading Oracle Docker image (~1-2 GB)
2. Starting Oracle container
3. Waiting for Oracle initialization (~2-3 minutes)

**Solution**: Subsequent runs are faster as image is cached.

---

### Q2: Can I use H2 database instead of Oracle?

**A**: No, for integration tests. Reasons:
- Oracle-specific SQL syntax in Flyway migrations
- Different transaction isolation behavior
- Different locking mechanisms
- Need production parity

For **unit tests**, H2 is acceptable and recommended.

---

### Q3: How do I know if Oracle container is ready?

**A**: Check container logs:

```bash
docker logs <container-id> | grep "DATABASE IS READY"
```

Or use Testcontainers wait strategy:

```java
.waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1))
```

---

### Q4: Tests pass locally but fail in CI/CD. Why?

**Possible causes**:

1. **Insufficient resources**
   ```yaml
   # GitHub Actions: Use larger runner
   runs-on: ubuntu-latest-4-cores
   ```

2. **Docker not available**
   ```yaml
   # Ensure Docker is available in CI
   services:
     docker:
       image: docker:dind
   ```

3. **Network restrictions**
   ```yaml
   # May need to configure proxy or use private registry
   ```

---

### Q5: How do I debug a failing optimistic locking test?

**Steps**:

1. **Enable SQL logging**
   ```yaml
   # application-integration-test.yml
   spring:
     jpa:
       show-sql: true
       properties:
         hibernate:
           format_sql: true
   ```

2. **Check version values**
   ```java
   System.out.println("Version before: " + entity.getVersion());
   repository.saveAndFlush(entity);
   System.out.println("Version after: " + entity.getVersion());
   ```

3. **Verify detach behavior**
   ```java
   entityManager.detach(entity2);
   System.out.println("Is managed: " + entityManager.contains(entity2));
   ```

---

### Q6: What if I don't have enough RAM for Oracle container?

**Options**:

1. **Use lighter Oracle image**
   ```java
   new OracleContainer("gvenzl/oracle-xe:slim")  // Smaller footprint
   ```

2. **Increase Docker memory limit**
   ```bash
   # Docker Desktop: Settings > Resources > Memory
   # Allocate at least 4GB
   ```

3. **Use external Oracle database** (see Solution 2)

---

### Q7: Can I run tests in parallel?

**A**: Not recommended for integration tests because:
- Testcontainers creates one container per test class
- Multiple Oracle containers consume significant resources
- May cause port conflicts

For **unit tests**, parallel execution is fine:

```bash
./gradlew test --parallel --max-workers=4
```

---

### Q8: How do I clean up old Docker images?

```bash
# Remove unused images
docker image prune -a

# Remove specific Oracle images
docker rmi gvenzl/oracle-free:slim-faststart

# Remove all stopped containers
docker container prune
```

---

### Q9: Tests fail with "No space left on device"

**Solution**:

```bash
# Check disk space
df -h

# Clean Docker system
docker system prune -a --volumes

# Remove old test containers
docker rm $(docker ps -a -q -f status=exited)
```

---

### Q10: How do I run only one specific test?

```bash
# Run single test method
./gradlew test --tests OptimisticLockingIntegrationTest.testUpdateProductOptimisticLocking

# Run all tests in class
./gradlew test --tests OptimisticLockingIntegrationTest

# Run tests matching pattern
./gradlew test --tests "*OptimisticLocking*"
```

---

## 📝 Summary

### Problem
- Integration tests fail due to Oracle database connection issues
- Root cause: Oracle container not running or not accessible

### Solution
- **Primary**: Ensure Docker/Podman is running and let Testcontainers manage Oracle
- **Alternative**: Use external Oracle database with custom test profile
- **Temporary**: Skip integration tests (not recommended for production)

### Key Takeaways
1. Integration tests **require** Oracle database (cannot use H2)
2. Testcontainers automatically manages database lifecycle
3. Failure is **infrastructure-related**, not application code issue
4. **Not related** to Resilience4j configuration
5. First run takes longer due to image download and Oracle initialization

---

**Next Steps**: Choose appropriate solution based on your environment and requirements. For most cases, Solution 1 (Testcontainers) is recommended.