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

### `BaseIntegrationTest` 為何採用 Singleton Container Pattern

`BaseIntegrationTest` 的 `OracleContainer` 是在 `static {}` 區塊手動 `start()` 的單例，**刻意不使用 `@Testcontainers` / `@Container`**。請勿改回 JUnit 的容器生命週期管理。

- **原因**：`@Container` 的生命週期會在**第一個**整合測試類別跑完後就停掉容器。但多個整合測試類別（如 `DemoApplicationTests`、`OptimisticLockingIntegrationTest`）的 `@SpringBootTest` + `@ActiveProfiles` 設定相同，Spring 會**快取並跨類別重用同一個 ApplicationContext**。被重用的 context（含 `@ServiceConnection` 連到的 DataSource）仍指向**已被停掉的舊容器 port**，於是後續類別的所有測試在 `beforeTestMethod` 取連線時打到死掉的 listener，整批 `ORA-12541 沒有監聽器` 失敗。
- **症狀**：`./gradlew test --tests "*OptimisticLockingIntegrationTest"` **單獨跑會過**，但完整 `./gradlew test` **必爆**；log 裡「容器啟動的 port」與「連線失敗的 port」**不一致**（連到的是前一個類別那個已停掉的容器）。
- **解法（現行做法）**：單例容器整個 JVM 只啟動一次、全程不被停，快取的 context 永遠指向活著的容器；容器由 Ryuk 在 JVM 結束時回收。`@ServiceConnection` 不需要 `@Container` 也能被 Spring Boot 掃描到（在 context 建立前容器已 `start()`）。

> 判讀提示：整合測試的失敗若「單跑會過、合跑才爆」且 port 對不上，幾乎都是容器生命週期與 context 快取的衝突，而非程式回歸。

### 本機跑整合測試（Windows + podman）注意事項

CI（GitHub Actions）環境正常，以下僅為**本機 Windows + podman machine** 的已知坑與解法：

1. **Testcontainers 找不到 Docker provider（`initializationError` / `DockerClientProviderStrategy`）**
   - 成因：`~/.testcontainers.properties` 預設用 `NpipeSocketClientProviderStrategy`，它找 `\\.\pipe\docker_engine`，但 podman 沒有這個 pipe。
   - 解法：把 `~/.testcontainers.properties` 設為指向 podman 的 pipe：
     ```properties
     docker.host=npipe:////./pipe/podman-machine-default
     ```
     （`podman system connection list` 可查到實際 pipe 名稱。）

2. **Oracle 連線被拒（`java.net.ConnectException` / `JDBCConnectionException`，容器卻顯示 healthy）**
   - 成因：podman machine 預設僅 2GiB 記憶體。若 `podman compose` 的 `oracle-db` 與 Testcontainers 另起的 `oracle-free` **同時運行**，兩個 Oracle 會記憶體不足，新容器起得來卻無法接受連線。**這是環境資源競爭，不是程式或 SQL 回歸。**
   - 解法：跑整合測試前先 `podman compose stop`（釋放記憶體），或 `podman machine set --memory 4096` 加大記憶體。

3. **純文件變更 push 不會啟動 Testcontainers**
   - 推送 `main` 時 pre-push hook 會跑 `./gradlew test`，但若沒有任何測試輸入（程式碼／資源）變更，test task 為 **UP-TO-DATE 快取**（數秒完成），**不會啟動 Testcontainers Oracle**。
   - 因此推送純文件（docs）變更時，**不需要先停 compose**，也不會撞上第 2 點的記憶體競爭。

> **`stop` 不是 `down`**：要暫停本機 compose 服務時用 `podman compose stop`（保留容器、網路、資料卷，可快速 `start` 回來）；`podman compose down` 會移除容器與網路、需重建，僅在明確要重建時才用。

> 發現新的環境坑與解法時，請補進本節，讓知識留在 repo 內供團隊與 CI 共享。
