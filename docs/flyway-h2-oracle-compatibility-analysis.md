# Flyway H2/Oracle 相容性方案分析

## 背景
test profile 使用 H2 資料庫,e2e/prod 使用 Oracle 資料庫。需要決定 Flyway migration 檔案的組織策略。

## 方案比較

### 方案 A: 單一 SQL 檔案 (相容語法)

#### 結構
```
src/main/resources/db/migration/
├── V1__initial_schema.sql (相容 H2 和 Oracle)
└── V2__initial_data.sql (可選)
```

#### 優點
1. **維護簡單**: 只需維護一套 SQL 檔案
2. **版本一致**: 所有環境使用相同的 migration 版本號
3. **減少錯誤**: 不會因為檔案不同步而產生 schema 差異
4. **易於理解**: 開發者只需關注一套 SQL

#### 缺點
1. **語法限制**: 必須使用兩者都支援的語法,無法使用特定資料庫的進階功能
2. **H2 Oracle 模式限制**: H2 的 Oracle 相容模式並非 100% 相容
3. **序列語法差異**: 
   - Oracle: `CREATE SEQUENCE seq_name START WITH 1 INCREMENT BY 1`
   - H2: 支援相同語法,但行為可能略有不同
4. **資料型別差異**:
   - `NVARCHAR2` 在 H2 中需要特別處理
   - `NUMBER(10)` 在 H2 中對應 `DECIMAL` 或 `NUMERIC`

#### 實際可行性
**可行** - H2 的 Oracle 模式 (`MODE=Oracle`) 已經支援大部分 Oracle 語法:
```sql
-- 這些語法在兩者都可用
CREATE TABLE ACCOUNT (
    ID NUMBER(10) NOT NULL,
    NAME NVARCHAR2(50) NOT NULL,
    STATUS VARCHAR2(1) NOT NULL,
    CONSTRAINT PK_ACCOUNT PRIMARY KEY (ID)
);

CREATE SEQUENCE account_id_seq START WITH 1 INCREMENT BY 1;
```

---

### 方案 B: 分別為 H2/Oracle 創建 Migration

#### 結構
```
src/main/resources/db/migration/
├── h2/
│   ├── V1__initial_schema.sql
│   └── V2__initial_data.sql
└── oracle/
    ├── V1__initial_schema.sql
    └── V2__initial_data.sql
```

#### 配置方式
```yaml
# application-test.yml
spring:
  flyway:
    locations: classpath:db/migration/h2

# application-e2e.yml / application-prod.yml
spring:
  flyway:
    locations: classpath:db/migration/oracle
```

#### 優點
1. **最佳化語法**: 可以使用各資料庫的原生語法和特性
2. **完全控制**: 針對不同資料庫的特性進行最佳化
3. **避免相容性問題**: 不需要擔心語法相容性
4. **未來擴展性**: 容易支援其他資料庫 (如 PostgreSQL)

#### 缺點
1. **維護成本高**: 需要同步維護兩套 SQL 檔案
2. **容易不同步**: Schema 可能因為忘記同步而產生差異
3. **測試複雜度**: 需要確保兩套 SQL 產生的 schema 一致
4. **版本管理複雜**: 需要確保版本號在兩個目錄中保持一致
5. **Code Review 負擔**: 每次變更需要檢查兩個檔案

#### 風險
- **Schema 漂移**: test 環境和 production 環境的 schema 可能不一致
- **測試失效**: 在 H2 測試通過,但在 Oracle 失敗的風險

---

## 方案 C: 混合方案 (條件判斷)

#### 結構
```
src/main/resources/db/migration/
├── V1__initial_schema.sql (包含條件判斷)
└── V2__initial_data.sql
```

#### 實作方式
使用 Flyway 的 placeholder 或 SQL 條件:
```sql
-- 使用 Flyway placeholder
${h2.specific.sql}
${oracle.specific.sql}
```

#### 優缺點
- **優點**: 單一檔案,但可以處理差異
- **缺點**: SQL 變得複雜難讀,Flyway 不原生支援條件執行

#### 評估
**不推薦** - 增加複雜度但收益有限

---

## 關於 Baseline 和測試資料的建議

### Baseline 配置

#### test profile
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: false  # 不需要,每次都是全新資料庫
    clean-disabled: false        # 允許 clean,方便測試
```

**理由**: 
- 單元測試每次都使用全新的 H2 in-memory 資料庫
- 不需要 baseline,直接執行 migration 即可

#### e2e profile
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true   # 需要,可能在已有資料的資料庫上執行
    clean-disabled: false        # 測試環境允許 clean
    clean-on-validation-error: true  # 驗證失敗時自動清理
```

**理由**:
- E2E 測試可能在已有資料的 Docker Oracle 上執行
- 允許 clean 方便重置測試環境

#### prod profile
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true    # 需要,生產環境可能已有資料
    clean-disabled: true         # 生產環境禁止 clean
    validate-on-migrate: true    # 嚴格驗證
```

**理由**:
- 生產環境必須禁止 clean 防止誤刪資料
- 使用 baseline 支援在已有資料庫上部署

### 測試資料策略

#### 選項 1: 由 TestDataInitializer 處理 (推薦)
```yaml
# 不在 V2__initial_data.sql 中加入測試資料
```

**優點**:
- 測試資料由程式碼管理,更靈活
- 可以根據不同測試場景動態生成
- 避免 SQL 檔案過於龐大

**缺點**:
- 需要確保 TestDataInitializer 在所有需要的 profile 中執行

#### 選項 2: V2__initial_data.sql (不推薦)
```sql
-- V2__initial_data.sql
INSERT INTO ACCOUNT (ID, NAME, STATUS, ...) VALUES (1, 'Test Account', 'Y', ...);
```

**優點**:
- 資料庫層面保證初始資料存在

**缺點**:
- 測試資料寫死在 SQL 中,不靈活
- 難以維護和更新
- 可能與 TestDataInitializer 衝突

---

## 推薦方案

### 最佳實踐: 方案 A (單一 SQL 相容語法)

**理由**:
1. 目前的 Entity 定義使用的都是標準 JPA 註解和 Oracle 語法
2. H2 的 `MODE=Oracle` 已經支援專案所需的所有語法
3. 維護成本低,不易出錯
4. Schema 一致性有保證

**實施細節**:
- 使用 `NUMBER(10)` 而非 `INT` (H2 Oracle 模式支援)
- 使用 `NVARCHAR2` (H2 Oracle 模式支援)
- 使用 `CREATE SEQUENCE` (H2 Oracle 模式支援)
- 使用 `TIMESTAMP` 而非 `DATETIME`

**配置**:
```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=Oracle;DB_CLOSE_DELAY=-1
  flyway:
    enabled: true
    baseline-on-migrate: false
    locations: classpath:db/migration

# application-e2e.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    clean-on-validation-error: true
    locations: classpath:db/migration

# application-prod.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    clean-disabled: true
    validate-on-migrate: true
    locations: classpath:db/migration
```

**測試資料**: 由 TestDataInitializer 處理,不在 migration 中加入

---

## 決策矩陣

| 考量因素 | 方案 A (單一檔案) | 方案 B (分離檔案) |
|---------|------------------|------------------|
| 維護成本 | ⭐⭐⭐⭐⭐ 低 | ⭐⭐ 高 |
| Schema 一致性 | ⭐⭐⭐⭐⭐ 保證 | ⭐⭐⭐ 需人工確保 |
| 語法彈性 | ⭐⭐⭐ 受限於相容性 | ⭐⭐⭐⭐⭐ 完全彈性 |
| 學習曲線 | ⭐⭐⭐⭐⭐ 簡單 | ⭐⭐⭐ 需理解兩套 |
| 錯誤風險 | ⭐⭐⭐⭐ 低 | ⭐⭐ 高 (不同步風險) |
| 適用性 | ⭐⭐⭐⭐⭐ 符合專案需求 | ⭐⭐⭐ 過度設計 |

**結論**: 對於此專案,**方案 A** 是最佳選擇。
