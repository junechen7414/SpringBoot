---
name: integration-test-runner
description: >
  Windows + podman 環境下執行整合測試的完整流程。
  處理 Testcontainers 找不到 Docker provider、Oracle 記憶體競爭等已知坑，
  確保「本機綠 = CI 綠」。觸發時機：使用者要跑 integration test、
  出現 DockerClientProviderStrategy 錯誤、ORA-12541 連線被拒等情況。
---

# Integration Test Runner (Windows + podman)

## 前置檢查：是否需要停 compose？

**先判斷這次 push 是否會真的跑 Testcontainers：**

- **有程式碼/資源變更** → test task **不會是 UP-TO-DATE** → 會啟動 Oracle container → **需要先 stop compose**
- **純文件變更（docs/）** → test task 為 UP-TO-DATE → **不啟動 container** → 不需要 stop compose，直接 push

## Step 1：停 compose（有程式碼變更時）

```bash
podman compose stop
```

> `stop` 保留容器和資料卷，之後可以快速 `start` 回來。
> **不要用 `podman compose down`**，那會移除容器與網路，需重建。

## Step 2：確認 testcontainers.properties

檢查 `~/.testcontainers.properties` 是否正確指向 podman pipe：

```properties
docker.host=npipe:////./pipe/podman-machine-default
```

如果檔案不存在或設定不對，用以下指令查詢實際 pipe 名稱：

```bash
podman system connection list
```

然後建立或修正 `~/.testcontainers.properties`。

## Step 3：執行測試

```bash
# 完整測試（含整合測試，Testcontainers 會自動啟動 Oracle）
./gradlew test

# 排除 SanityTest（pre-push hook / CI gate 的同等指令）
./gradlew test -Djunit.platform.exclude.tags=SanityTest

# 只跑整合測試
./gradlew test --tests "*IntegrationTest"

# 單一測試類別
./gradlew test --tests "com.ibm.demo.order.OrderServiceTest"
```

> `test` task 強制 `maxParallelForks = 1`，不會平行執行。

## 已知坑與診斷

### 坑 1：Testcontainers 找不到 Docker provider

**錯誤特徵**：`initializationError`、`DockerClientProviderStrategy`、`Cannot connect to Docker daemon`

**原因**：`~/.testcontainers.properties` 沒設定，預設找 `\\.\pipe\docker_engine`（docker 的 pipe），但本機用的是 podman。

**解法**：見 Step 2。

---

### 坑 2：Oracle 連線被拒（容器卻顯示 healthy）

**錯誤特徵**：`java.net.ConnectException`、`JDBCConnectionException`、`ORA-12541 沒有監聽器`

**原因**：podman machine 預設 2GiB 記憶體。若 `podman compose` 的 `oracle-db` 與 Testcontainers 的 `oracle-free` **同時運行**，記憶體不足，新容器啟動後無法接受連線。

**解法（擇一）**：
1. 跑測試前先 `podman compose stop`（見 Step 1）
2. 或擴大 podman machine 記憶體：
   ```bash
   podman machine stop
   podman machine set --memory 4096
   podman machine start
   ```

---

### 坑 3：合跑爆、單跑過（port 對不上）

**錯誤特徵**：單獨跑某個 `*IntegrationTest` 通過，但 `./gradlew test` 全跑必爆；log 顯示「容器啟動的 port」與「連線失敗的 port」不一致。

**原因**：這是 singleton container pattern 被破壞的症狀。`BaseIntegrationTest` 使用 `static {}` 啟動單例 Oracle container，**不使用 `@Testcontainers`/`@Container`**。若有人改成了 `@Container`，lifecycle 會在第一個 test class 後停容器，但 Spring 快取的 context 仍指向舊 port。

**解法**：確認 `BaseIntegrationTest` 的 container 是 `static {}` 手動 `start()`，而非 `@Container`。**不要改成 `@Testcontainers`/`@Container`**。

---

## 測試完成後（有需要時）

重啟 compose：

```bash
podman compose start
# 或完整啟動
podman compose up -d
```
