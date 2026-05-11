# Playwright Repository 修改指南

## 問題說明

當 Playwright 測試 workflow 執行 `docker compose down` 時出現警告：

```
time="2026-05-10T05:12:18Z" level=warning msg="The \"ORACLE_USERNAME\" variable is not set. Defaulting to a blank string."
time="2026-05-10T05:12:18Z" level=warning msg="The \"ORACLE_PASSWORD\" variable is not set. Defaulting to a blank string."
```

**根本原因：**
- SpringBoot backend repo 使用環境特定的變數名稱：`ORACLE_TEST_USERNAME` 和 `ORACLE_TEST_PASSWORD`
- Playwright repo 的 `docker-compose.test.yml` 使用通用名稱：`ORACLE_USERNAME` 和 `ORACLE_PASSWORD`
- 變數名稱不匹配導致 Docker Compose 找不到環境變數

## 解決方案

需要在 **Playwright Repository** 中進行以下三個修改：

---

## 修改 1: 更新 docker-compose.test.yml

### 原始內容（需要修改的部分）

```yaml
services:
  app:
    environment:
      - SPRING_DATASOURCE_USERNAME=${ORACLE_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${ORACLE_PASSWORD}
  
  oracle-db:
    environment:
      - ORACLE_USERNAME=${ORACLE_USERNAME}
      - ORACLE_PASSWORD=${ORACLE_PASSWORD}
    healthcheck:
      test: [ "CMD-SHELL", "sqlplus -L ${ORACLE_USERNAME}/${ORACLE_PASSWORD}@FREEPDB1 <<<'exit;'" ]
```

### 修改後內容

```yaml
services:
  app:
    environment:
      - SPRING_DATASOURCE_USERNAME=${ORACLE_TEST_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${ORACLE_TEST_PASSWORD}
  
  oracle-db:
    environment:
      - ORACLE_USERNAME=${ORACLE_TEST_USERNAME}
      - ORACLE_PASSWORD=${ORACLE_TEST_PASSWORD}
    healthcheck:
      test: [ "CMD-SHELL", "sqlplus -L ${ORACLE_TEST_USERNAME}/${ORACLE_TEST_PASSWORD}@FREEPDB1 <<<'exit;'" ]
```

**變更說明：**
- 將所有 `${ORACLE_USERNAME}` 替換為 `${ORACLE_TEST_USERNAME}`
- 將所有 `${ORACLE_PASSWORD}` 替換為 `${ORACLE_TEST_PASSWORD}`
- 確保 healthcheck 命令也使用新的變數名稱

---

## 修改 2: 更新 GitHub Secrets

在 Playwright Repository 的 **Settings → Secrets and variables → Actions** 中：

### 需要添加的新 Secrets

| Secret Name | Value | 說明 |
|------------|-------|------|
| `ORACLE_TEST_USERNAME` | `BOBBY_TEST` | Oracle 測試環境使用者名稱 |
| `ORACLE_TEST_PASSWORD` | `BOBBY7414` | Oracle 測試環境密碼 |

### 可選：保留舊 Secrets（向後兼容）

如果有其他地方還在使用舊的變數名稱，可以暫時保留：
- `ORACLE_USERNAME` → 可以刪除或保留
- `ORACLE_PASSWORD` → 可以刪除或保留

**建議：** 完成遷移後刪除舊的 Secrets，避免混淆。

---

## 修改 3: 更新 Workflow YAML

### 原始 workflow（需要修改的部分）

```yaml
- name: Start Test Environment
  env:
    ORACLE_USERNAME: ${{ secrets.ORACLE_USERNAME }}
    ORACLE_PASSWORD: ${{ secrets.ORACLE_PASSWORD }}
  run: |
    docker compose -f docker-compose.test.yml up -d

- name: Stop Environment
  if: always()
  env:
    ORACLE_USERNAME: ${{ secrets.ORACLE_USERNAME }}
    ORACLE_PASSWORD: ${{ secrets.ORACLE_PASSWORD }}
  run: |
    docker compose -f docker-compose.test.yml down -v
```

### 修改後 workflow

```yaml
- name: Start Test Environment
  env:
    ORACLE_TEST_USERNAME: ${{ secrets.ORACLE_TEST_USERNAME }}
    ORACLE_TEST_PASSWORD: ${{ secrets.ORACLE_TEST_PASSWORD }}
  run: |
    docker compose -f docker-compose.test.yml up -d

- name: Stop Environment
  if: always()
  env:
    ORACLE_TEST_USERNAME: ${{ secrets.ORACLE_TEST_USERNAME }}
    ORACLE_TEST_PASSWORD: ${{ secrets.ORACLE_TEST_PASSWORD }}
  run: |
    docker compose -f docker-compose.test.yml down -v
```

**變更說明：**
- 將 `ORACLE_USERNAME` 改為 `ORACLE_TEST_USERNAME`
- 將 `ORACLE_PASSWORD` 改為 `ORACLE_TEST_PASSWORD`
- 確保 `up` 和 `down` 步驟都有 `env` 區塊

---

## 完整的修改檢查清單

### ✅ 步驟 1: 修改 docker-compose.test.yml
- [ ] 更新 `app` 服務的環境變數
- [ ] 更新 `oracle-db` 服務的環境變數
- [ ] 更新 `oracle-db` 的 healthcheck 命令
- [ ] 提交並推送變更

### ✅ 步驟 2: 配置 GitHub Secrets
- [ ] 添加 `ORACLE_TEST_USERNAME` secret
- [ ] 添加 `ORACLE_TEST_PASSWORD` secret
- [ ] （可選）刪除舊的 `ORACLE_USERNAME` 和 `ORACLE_PASSWORD`

### ✅ 步驟 3: 更新 Workflow
- [ ] 找到所有使用 `ORACLE_USERNAME` 的地方
- [ ] 替換為 `ORACLE_TEST_USERNAME`
- [ ] 找到所有使用 `ORACLE_PASSWORD` 的地方
- [ ] 替換為 `ORACLE_TEST_PASSWORD`
- [ ] 確認 `env` 區塊在所有 docker compose 命令中都存在
- [ ] 提交並推送變更

### ✅ 步驟 4: 驗證
- [ ] 觸發 workflow 執行
- [ ] 檢查 "Stop Environment" 步驟的日誌
- [ ] 確認沒有環境變數警告訊息
- [ ] 確認測試正常執行

---

## 範例：完整的 Workflow 片段

```yaml
name: E2E Tests with Backend

on:
  repository_dispatch:
    types: [backend_image_updated]
  workflow_dispatch:

jobs:
  e2e-tests:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout Playwright Tests
        uses: actions/checkout@v6

      - name: Start Test Environment
        env:
          ORACLE_TEST_USERNAME: ${{ secrets.ORACLE_TEST_USERNAME }}
          ORACLE_TEST_PASSWORD: ${{ secrets.ORACLE_TEST_PASSWORD }}
        run: |
          docker compose -f docker-compose.test.yml up -d
          
      - name: Wait for Application to be Ready
        run: |
          timeout 300 bash -c 'until curl -f http://localhost:8787/actuator/health; do sleep 5; done'

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          
      - name: Install Dependencies
        run: npm ci

      - name: Run Playwright Tests
        run: npm run test:e2e

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-report
          path: playwright-report/

      - name: Stop Environment
        if: always()
        env:
          ORACLE_TEST_USERNAME: ${{ secrets.ORACLE_TEST_USERNAME }}
          ORACLE_TEST_PASSWORD: ${{ secrets.ORACLE_TEST_PASSWORD }}
        run: |
          docker compose -f docker-compose.test.yml down -v
```

---

## 故障排除

### 問題 1: 仍然看到環境變數警告

**可能原因：**
- GitHub Secrets 名稱拼寫錯誤
- Workflow 中的 `env` 區塊遺漏
- docker-compose.test.yml 未更新

**解決方法：**
1. 檢查 Secrets 名稱是否完全匹配（區分大小寫）
2. 確認 workflow 的每個 docker compose 命令都有 `env` 區塊
3. 確認 docker-compose.test.yml 使用正確的變數名稱

### 問題 2: 資料庫連接失敗

**可能原因：**
- Secret 值不正確
- 變數名稱在某處未更新

**解決方法：**
1. 驗證 Secret 值：`BOBBY_TEST` 和 `BOBBY7414`
2. 搜尋整個 repo 確認沒有遺漏的 `ORACLE_USERNAME`
3. 檢查 docker-compose.test.yml 的 healthcheck 是否使用新變數

### 問題 3: Workflow 執行失敗

**可能原因：**
- YAML 語法錯誤
- Secret 未設定

**解決方法：**
1. 使用 YAML linter 檢查語法
2. 確認 Secrets 已在 repository settings 中設定
3. 檢查 workflow 日誌中的具體錯誤訊息

---

## 環境變數命名規範

### Backend Repository (SpringBoot)
```
ORACLE_DEV_USERNAME   # 開發環境
ORACLE_DEV_PASSWORD
ORACLE_TEST_USERNAME  # 測試環境
ORACLE_TEST_PASSWORD
```

### Playwright Repository
```
ORACLE_TEST_USERNAME  # 對應 Backend 的測試環境
ORACLE_TEST_PASSWORD
```

**重要：** 兩個 repository 的測試環境使用相同的變數名稱，確保一致性。

---

## 遷移時間表

1. **準備階段**（5 分鐘）
   - 閱讀本指南
   - 準備 Secret 值

2. **執行階段**（15 分鐘）
   - 修改 docker-compose.test.yml
   - 設定 GitHub Secrets
   - 更新 workflow YAML
   - 提交變更

3. **驗證階段**（10 分鐘）
   - 觸發 workflow
   - 檢查日誌
   - 確認測試通過

**總計：約 30 分鐘**

---

## 相關資源

- [Backend Repository](https://github.com/[your-org]/SpringBoot)
- [Backend 環境變數說明](https://github.com/[your-org]/SpringBoot/blob/main/.env.example)
- [Docker Compose 環境變數文檔](https://docs.docker.com/compose/environment-variables/)
- [GitHub Actions Secrets 文檔](https://docs.github.com/en/actions/security-guides/encrypted-secrets)

---

## 支援

如有問題，請：
1. 檢查本指南的故障排除章節
2. 查看 workflow 執行日誌
3. 聯繫 Backend 團隊：Bobby Chen