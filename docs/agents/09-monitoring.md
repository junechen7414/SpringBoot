## 監控與可觀測性

### 指標收集鏈路

```
Spring Boot App (OTLP) → Grafana Alloy → Prometheus → Grafana
```

### 關鍵端點

- **健康檢查**: `/actuator/health`（亦為映像內建 `HEALTHCHECK` 的探測目標，下游 E2E repo 依賴此健康狀態判斷就緒；契約細節見 [02-setup.md](./02-setup.md#映像內建-healthcheck重要契約)）
- **指標**: `/actuator/metrics`
- **Prometheus**: `/actuator/prometheus`

### 健康檢查（HEALTHCHECK）的運作機制

映像的 `HEALTHCHECK`（見 `Dockerfile`）**不是建置指令**，而是烙進 image 的 runtime metadata；容器跑起來後才由容器 runtime 反覆執行。

**三個對象（誰、在哪、扮演什麼）**

| 對象 | 位置 | 角色 |
|------|------|------|
| 容器 runtime（Podman / Docker） | 主機上，容器**外** | 監工：建容器、定時檢查、判定 healthy/unhealthy。**不對 app 發 HTTP** |
| Spring Boot app | 容器**內**，聽 8787 | HTTP **server** |
| wget | 容器**內**（runtime 派出的探針行程） | HTTP **client** |

**流程**

1. runtime 讀 image 的 HEALTHCHECK 設定（每 30s、timeout 30s、啟動 60s 後開始、連續失敗 3 次判 unhealthy）。
2. 每次 runtime 在容器內生一個 wget 行程，執行 `wget --spider http://localhost:8787/actuator/health`。
3. wget 對**同一容器內**的 app 發 HTTP（`localhost` = 容器自身的網路命名空間）。
4. wget 依結果給退出碼（0 成功／非 0 失敗）。
5. runtime **只看退出碼**（看不到 HTTP 內容），據此更新容器健康狀態。

也就是說：runtime 是「觸發者/裁判」，wget 才是「實際發 HTTP 的 client」，app 是 server。runtime 與 Spring Boot **不是** HTTP 的 client/server 關係，而是「監工 → 被監管的行程」。

**與 Spring Security 的關係（重要）**

wget 的請求跟外部請求走**同一條** servlet filter chain。若 `/actuator/health` 未 `permitAll`，wget 無憑證 → 回 **401** → 退出碼非 0 → 連續失敗 → 容器被標 **unhealthy**。因此 `SecurityConfig` 明確放行 `/actuator/health`（見 [06-architecture.md](./06-architecture.md) 安全段）。

### Resilience4j 監控指標

- `resilience4j.circuitbreaker.state`: 熔斷器狀態
- `resilience4j.bulkhead.available.concurrent.calls`: 可用許可數
- `resilience4j.circuitbreaker.failure.rate`: 失敗率

### CI/CD 流程

#### GitHub Actions Workflow

1. **單元測試**: 執行 `./gradlew test` 作為 Quality Gate（排除 SanityTest）
2. **Docker 建置**: 多階段建置，僅在容器內執行 `bootJar`（跳過測試）
3. **映像檔推送**: 推送至 GitHub Container Registry (GHCR)
4. **觸發 E2E**: 透過 `repository_dispatch` 通知 E2E 測試專案
5. **文件生成** (獨立 Job，依賴 build-and-push):
   - 執行 `./gradlew generateOpenApiDocs` 產生 `swagger.json`
   - Checkout 目標 repo（保留現有文件）
   - 僅複製 `swagger.json` 至目標 repo 的 `docs/` 目錄
   - Commit and push（使用 checkout+copy 方式，避免覆蓋目標 repo 其他文件）

#### 快取策略

- Gradle 依賴快取: `actions/setup-java` 的 `cache: gradle`
- Docker Layer 快取: `type=gha,scope=${{ github.ref_name }}`
