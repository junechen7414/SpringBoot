# Playwright Repository 快速修復指南

> 🎯 **目標：** 消除 `docker compose down` 時的環境變數警告訊息

---

## 🔧 需要修改的 3 個地方

### 1️⃣ docker-compose.test.yml

**搜尋並替換：**

```bash
# 在 Playwright repo 中執行
sed -i 's/${ORACLE_USERNAME}/${ORACLE_TEST_USERNAME}/g' docker-compose.test.yml
sed -i 's/${ORACLE_PASSWORD}/${ORACLE_TEST_PASSWORD}/g' docker-compose.test.yml
```

**或手動修改：**
- `${ORACLE_USERNAME}` → `${ORACLE_TEST_USERNAME}`
- `${ORACLE_PASSWORD}` → `${ORACLE_TEST_PASSWORD}`

**影響位置：**
- `app` 服務的 `environment` 區塊
- `oracle-db` 服務的 `environment` 區塊
- `oracle-db` 服務的 `healthcheck` 命令

---

### 2️⃣ GitHub Secrets

**Settings → Secrets and variables → Actions → New repository secret**

添加兩個新 Secrets：

| Name | Value |
|------|-------|
| `ORACLE_TEST_USERNAME` | `BOBBY_TEST` |
| `ORACLE_TEST_PASSWORD` | `BOBBY7414` |

---

### 3️⃣ Workflow YAML (.github/workflows/*.yml)

**搜尋並替換：**

```bash
# 在 Playwright repo 中執行
find .github/workflows -name "*.yml" -exec sed -i 's/ORACLE_USERNAME/ORACLE_TEST_USERNAME/g' {} +
find .github/workflows -name "*.yml" -exec sed -i 's/ORACLE_PASSWORD/ORACLE_TEST_PASSWORD/g' {} +
```

**或手動修改所有 workflow 文件中的：**
- `ORACLE_USERNAME:` → `ORACLE_TEST_USERNAME:`
- `secrets.ORACLE_USERNAME` → `secrets.ORACLE_TEST_USERNAME`
- `ORACLE_PASSWORD:` → `ORACLE_TEST_PASSWORD:`
- `secrets.ORACLE_PASSWORD` → `secrets.ORACLE_TEST_PASSWORD`

---

## ✅ 驗證步驟

1. 提交並推送所有變更
2. 觸發 workflow 執行
3. 檢查 "Stop Environment" 步驟的日誌
4. ✅ 確認沒有警告訊息

---

## 📋 變更前後對照

### docker-compose.test.yml

```diff
  app:
    environment:
-     - SPRING_DATASOURCE_USERNAME=${ORACLE_USERNAME}
-     - SPRING_DATASOURCE_PASSWORD=${ORACLE_PASSWORD}
+     - SPRING_DATASOURCE_USERNAME=${ORACLE_TEST_USERNAME}
+     - SPRING_DATASOURCE_PASSWORD=${ORACLE_TEST_PASSWORD}
  
  oracle-db:
    environment:
-     - ORACLE_USERNAME=${ORACLE_USERNAME}
-     - ORACLE_PASSWORD=${ORACLE_PASSWORD}
+     - ORACLE_USERNAME=${ORACLE_TEST_USERNAME}
+     - ORACLE_PASSWORD=${ORACLE_TEST_PASSWORD}
    healthcheck:
-     test: [ "CMD-SHELL", "sqlplus -L ${ORACLE_USERNAME}/${ORACLE_PASSWORD}@FREEPDB1 <<<'exit;'" ]
+     test: [ "CMD-SHELL", "sqlplus -L ${ORACLE_TEST_USERNAME}/${ORACLE_TEST_PASSWORD}@FREEPDB1 <<<'exit;'" ]
```

### workflow.yml

```diff
  - name: Start Test Environment
    env:
-     ORACLE_USERNAME: ${{ secrets.ORACLE_USERNAME }}
-     ORACLE_PASSWORD: ${{ secrets.ORACLE_PASSWORD }}
+     ORACLE_TEST_USERNAME: ${{ secrets.ORACLE_TEST_USERNAME }}
+     ORACLE_TEST_PASSWORD: ${{ secrets.ORACLE_TEST_PASSWORD }}
    run: |
      docker compose -f docker-compose.test.yml up -d

  - name: Stop Environment
    if: always()
    env:
-     ORACLE_USERNAME: ${{ secrets.ORACLE_USERNAME }}
-     ORACLE_PASSWORD: ${{ secrets.ORACLE_PASSWORD }}
+     ORACLE_TEST_USERNAME: ${{ secrets.ORACLE_TEST_USERNAME }}
+     ORACLE_TEST_PASSWORD: ${{ secrets.ORACLE_TEST_PASSWORD }}
    run: |
      docker compose -f docker-compose.test.yml down -v
```

---

## 🚨 常見錯誤

❌ **只修改了 workflow，忘記修改 docker-compose.test.yml**
- 結果：仍然會有警告

❌ **只修改了 docker-compose.test.yml，忘記更新 Secrets**
- 結果：變數找不到，容器無法啟動

❌ **Secrets 名稱拼寫錯誤**
- 結果：變數為空，連接失敗

✅ **正確做法：三個地方都要改，且名稱必須完全一致**

---

## 📞 需要幫助？

查看完整指南：[`docs/playwright-repo-migration-guide.md`](./playwright-repo-migration-guide.md)