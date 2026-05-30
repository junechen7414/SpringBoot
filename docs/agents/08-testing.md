## 測試策略

### 單元測試 (Unit Test)

- 使用 Mockito 模擬依賴
- 遵循 AAA 模式 (Arrange, Act, Assert)
- 測試類別標註 `@ExtendWith(MockitoExtension.class)`

### 整合測試 (Integration Test)

- 繼承 `BaseIntegrationTest` 自動啟動 Testcontainers
- 使用真實資料庫驗證 SQL 語法與業務流程
- 標註 `@ActiveProfiles("integration-test")`

### 測試資料初始化

- **靜態資料**: 使用 SQL 檔案 (`data.sql`)
- **動態資料**: 使用 `CommandLineRunner` 搭配 `@Profile("dev")`
- **E2E 驗證**: 透過 REST API 建立測試資料

### 為何整合測試使用 Testcontainers？

- **環境一致性**: 使用與生產環境相同的 Oracle 映像檔
- **完整驗證**: 支援所有 SQL 語法、Stored Procedures
- **CI/CD 友善**: 無需預先安裝資料庫，容器自動管理生命週期
