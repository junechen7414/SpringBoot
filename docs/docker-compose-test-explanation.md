# docker-compose.test.yml 說明

## 文件位置

此 SpringBoot repository 根目錄下的 [`docker-compose.test.yml`](../docker-compose.test.yml) 文件。

## 用途

這個文件是為了**向後兼容**而創建的，使用舊的環境變數命名方式：
- `ORACLE_USERNAME`
- `ORACLE_PASSWORD`

## ⚠️ 重要說明

**這個文件不應該被使用！** 它的存在只是為了：

1. **文檔目的**：展示舊的配置方式
2. **過渡期參考**：如果有其他系統還在使用舊的變數名稱

## 正確的做法

### 對於 Playwright Repository

Playwright repo 應該使用**新的環境變數命名**：
- `ORACLE_TEST_USERNAME`
- `ORACLE_TEST_PASSWORD`

請參考：
- [Playwright Repository 修改指南](./playwright-repo-migration-guide.md)
- [快速修復指南](./playwright-quick-fix.md)

### 對於本地開發

使用 [`docker-compose.yml`](../docker-compose.yml)，它使用：
- `ORACLE_DEV_USERNAME`
- `ORACLE_DEV_PASSWORD`

## 建議

**建議刪除 `docker-compose.test.yml`**，因為：

1. ❌ 使用舊的變數命名，與專案標準不一致
2. ❌ 可能造成混淆
3. ❌ Playwright repo 應該有自己的 `docker-compose.test.yml`

## 正確的架構

```
SpringBoot Repository (Backend)
├── docker-compose.yml          ✅ 本地開發用（ORACLE_DEV_*）
├── .env                        ✅ 包含所有環境的變數
└── .env.example                ✅ 變數範本

Playwright Repository (E2E Tests)
├── docker-compose.test.yml     ✅ 測試用（ORACLE_TEST_*）
└── .github/workflows/*.yml     ✅ 使用 ORACLE_TEST_* secrets
```

## 環境變數標準

| Repository | 環境 | 變數名稱 |
|-----------|------|---------|
| SpringBoot | 開發 | `ORACLE_DEV_USERNAME`<br>`ORACLE_DEV_PASSWORD` |
| SpringBoot | 測試 | `ORACLE_TEST_USERNAME`<br>`ORACLE_TEST_PASSWORD` |
| Playwright | CI/CD | `ORACLE_TEST_USERNAME`<br>`ORACLE_TEST_PASSWORD` |

**一致性原則：** Playwright 的 CI/CD 環境使用與 SpringBoot 測試環境相同的變數名稱。