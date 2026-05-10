# 測試資料初始化改善方案說明

## 📋 改善概述

本次改善針對測試資料初始化機制進行優化，解決了原有 `create-drop` 策略導致的資料不持久問題。

---

## 🔧 實施的變更

### 1. 修改 DDL 策略 (`application-dev.yml`)

**變更前：**
```yaml
jpa:
  hibernate:
    ddl-auto: create-drop  # 每次重啟都清空資料庫
```

**變更後：**
```yaml
jpa:
  hibernate:
    ddl-auto: update  # 保留資料，僅更新 schema
  show-sql: true  # 開發環境顯示 SQL 語句
```

**優點：**
- ✅ 資料在應用重啟後仍然保留
- ✅ Schema 變更時自動更新表結構
- ✅ 避免每次啟動都重建資料庫的時間成本

---

### 2. 改進測試資料初始化邏輯 (`TestDataInitializer.java`)

**核心改善：**

#### A. 加入資料存在性檢查
```java
long accountCount = accountRepository.count();
long productCount = productRepository.count();

if (accountCount > 0 && productCount > 0) {
    logger.info("測試資料已存在，跳過初始化流程");
    return;
}
```

**效果：**
- 避免重複插入資料
- 應用重啟時不會產生重複記錄
- 提升啟動速度

#### B. 加入詳細日誌記錄
```java
logger.info("=== 開始檢查測試資料初始化狀態 ===");
logger.info("當前資料庫狀態 - 帳戶數量: {}, 商品數量: {}", accountCount, productCount);
```

**效果：**
- 清楚了解初始化流程執行狀態
- 方便除錯與問題追蹤

#### C. 程式碼結構優化
- 將初始化邏輯提取為獨立方法 `initializeTestData()`
- 加入完整的 JavaDoc 註解
- 提升程式碼可讀性與維護性

---

## 📊 使用情境

### 情境 1: 首次啟動應用
```
=== 開始檢查測試資料初始化狀態 ===
當前資料庫狀態 - 帳戶數量: 0, 商品數量: 0
開始建立測試資料...
儲存 100 筆商品資料...
儲存 100 筆帳戶資料...
測試資料建立成功 - 帳戶: 100, 商品: 100
=== 測試資料初始化完成 ===
```

### 情境 2: 重啟應用（資料已存在）
```
=== 開始檢查測試資料初始化狀態 ===
當前資料庫狀態 - 帳戶數量: 100, 商品數量: 100
測試資料已存在，跳過初始化流程
```

---

## 🔄 如何重置測試資料

### 方法 1: 手動清空資料庫（推薦）
```sql
-- 連線到 Oracle 資料庫
TRUNCATE TABLE ORDER_PRODUCT_DETAIL;
TRUNCATE TABLE ORDER_INFO;
TRUNCATE TABLE PRODUCT;
TRUNCATE TABLE ACCOUNT;
```

### 方法 2: 暫時改回 create-drop
```yaml
# application-dev.yml (僅用於重置，完成後請改回 update)
jpa:
  hibernate:
    ddl-auto: create-drop
```

### 方法 3: 使用 Docker Volume 重建
```bash
# 停止並移除容器與 Volume
docker-compose down -v

# 重新啟動（會建立全新的資料庫）
docker-compose up -d
```

---

## ⚠️ 注意事項

### 1. Profile 設定
- 此初始化器僅在 `dev` profile 啟用
- 確保生產環境不會誤觸此邏輯
- 檢查方式：查看 `application.yml` 中的 `spring.profiles.active`

### 2. 資料一致性
- 若手動修改了測試資料，重啟應用不會重置
- 需要手動清空或使用上述重置方法

### 3. Entity 變更
- 當 Entity 欄位變更時，`ddl-auto: update` 會自動更新表結構
- 但無法處理複雜的 schema 變更（如欄位重命名）
- 建議：長期應遷移至 Flyway/Liquibase

---

## 🚀 後續改善建議

### 短期（已完成）
- ✅ 改為 `ddl-auto: update`
- ✅ 加入資料存在性檢查
- ✅ 加入日誌記錄

### 中期（建議實施）
- [ ] 將測試資料數量設定為可配置參數
- [ ] 支援不同類型的測試資料集（小型/中型/大型）
- [ ] 加入測試資料重置 API endpoint

### 長期（架構升級）
- [ ] 引入 Flyway 進行資料庫版本管理
- [ ] 建立 `db/migration` 目錄結構
- [ ] 將 schema 定義與測試資料分離為不同版本的遷移腳本

---

## 📚 相關文件

- [專案架構說明](../README.md)
- [資料庫遷移計畫](../migration-plan.md)
- [Docker Compose 配置](../docker-compose.yml)

---

## 🔗 相關檔案

- 配置檔案: [`src/main/resources/application-dev.yml`](../src/main/resources/application-dev.yml)
- 初始化器: [`src/main/java/com/ibm/demo/testdata/TestDataInitializer.java`](../src/main/java/com/ibm/demo/testdata/TestDataInitializer.java)
- Entity 定義:
  - [`Account.java`](../src/main/java/com/ibm/demo/account/Account.java)
  - [`Product.java`](../src/main/java/com/ibm/demo/product/Product.java)

---

**最後更新**: 2026-05-10  
**版本**: 1.0.0  
**狀態**: ✅ 已實施