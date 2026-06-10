## 監控與可觀測性

### 指標收集鏈路

```
Spring Boot App (OTLP) → Grafana Alloy → Prometheus → Grafana
```

### 關鍵端點

- **健康檢查**: `/actuator/health`
- **指標**: `/actuator/metrics`
- **Prometheus**: `/actuator/prometheus`

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
