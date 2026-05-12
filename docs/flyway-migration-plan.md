# Flyway 遷移計劃

## 目標
將專案從 JPA DDL-auto 遷移到 Flyway 進行資料庫版本控制管理。

## 環境策略

### 保持 DDL-auto 的環境
- **dev**: 開發環境,使用 `ddl-auto: create-drop` + `flyway.enabled: false`
- **openapi**: 文檔生成環境,使用 H2 + `ddl-auto: create-drop` + `flyway.enabled: false`

### 改用 Flyway 的環境  
- **test**: 單元測試環境,改用 H2 + Flyway
- **e2e**: E2E 測試環境,改用 Oracle + Flyway

## 資料表結構分析

### 1. ACCOUNT 表
```sql
CREATE TABLE ACCOUNT (
    ID NUMBER(10) NOT NULL,
    NAME NVARCHAR2(50) NOT NULL,
    STATUS VARCHAR2(1) NOT NULL,
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    DELETED NUMBER(1) DEFAULT 0,
    DELETED_AT TIMESTAMP,
    VERSION NUMBER(10) DEFAULT 0 NOT NULL,
    CONSTRAINT PK_ACCOUNT PRIMARY KEY (ID)
);

CREATE SEQUENCE account_id_seq START WITH 1 INCREMENT BY 1;
```

**欄位說明:**
- `ID`: 主鍵,使用序列 account_id_seq
- `NAME`: 帳戶名稱
- `STATUS`: 啟用狀態 (Y/N)
- `CREATED_AT`: 創建時間 (BaseEntity)
- `UPDATED_AT`: 更新時間 (BaseEntity)
- `DELETED`: 軟刪除標記 (BaseEntity)
- `DELETED_AT`: 刪除時間 (BaseEntity)
- `VERSION`: 樂觀鎖版本號 (BaseEntity)

**SQLRestriction**: `STATUS = 'Y' AND DELETED = 0`

### 2. PRODUCT 表
```sql
CREATE TABLE PRODUCT (
    ID NUMBER(10) NOT NULL,
    NAME NVARCHAR2(100) NOT NULL,
    PRICE NUMBER(12,4) NOT NULL,
    SALE_STATUS NUMBER(4) NOT NULL,
    AVAILABLE NUMBER(10) NOT NULL DEFAULT 0,
    RESERVED NUMBER(10) NOT NULL DEFAULT 0,
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    DELETED NUMBER(1) DEFAULT 0,
    DELETED_AT TIMESTAMP,
    VERSION NUMBER(10) DEFAULT 0 NOT NULL,
    CONSTRAINT PK_PRODUCT PRIMARY KEY (ID)
);

CREATE SEQUENCE product_id_seq START WITH 1 INCREMENT BY 1;
```

**欄位說明:**
- `ID`: 主鍵,使用序列 product_id_seq
- `NAME`: 商品名稱
- `PRICE`: 價格 (精度 12,4)
- `SALE_STATUS`: 銷售狀態 (1001=可銷售)
- `AVAILABLE`: 可用庫存
- `RESERVED`: 預留庫存
- 其他欄位繼承自 BaseEntity

**SQLRestriction**: `DELETED = 0 AND SALE_STATUS = 1001`

### 3. ORDER_INFO 表
```sql
CREATE TABLE ORDER_INFO (
    ID NUMBER(10) NOT NULL,
    ACCOUNT_ID NUMBER(10) NOT NULL,
    STATUS NUMBER(4) NOT NULL,
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    DELETED NUMBER(1) DEFAULT 0,
    DELETED_AT TIMESTAMP,
    VERSION NUMBER(10) DEFAULT 0 NOT NULL,
    CONSTRAINT PK_ORDER_INFO PRIMARY KEY (ID),
    CONSTRAINT FK_ORDER_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES ACCOUNT(ID)
);

CREATE SEQUENCE order_id_seq START WITH 1 INCREMENT BY 1;
```

**欄位說明:**
- `ID`: 主鍵,使用序列 order_id_seq
- `ACCOUNT_ID`: 外鍵,關聯到 ACCOUNT 表
- `STATUS`: 訂單狀態 (1001=進行中)
- 其他欄位繼承自 BaseEntity

**SQLRestriction**: `DELETED = 0 AND STATUS=1001`

### 4. ORDER_PRODUCT_DETAIL 表
```sql
CREATE TABLE ORDER_PRODUCT_DETAIL (
    ID NUMBER(10) NOT NULL,
    ORDER_ID NUMBER(10) NOT NULL,
    PRODUCT_ID NUMBER(10) NOT NULL,
    QUANTITY NUMBER(10) NOT NULL,
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    DELETED NUMBER(1) DEFAULT 0,
    DELETED_AT TIMESTAMP,
    VERSION NUMBER(10) DEFAULT 0 NOT NULL,
    CONSTRAINT PK_ORDER_DETAIL PRIMARY KEY (ID),
    CONSTRAINT FK_DETAIL_ORDER FOREIGN KEY (ORDER_ID) REFERENCES ORDER_INFO(ID),
    CONSTRAINT FK_DETAIL_PRODUCT FOREIGN KEY (PRODUCT_ID) REFERENCES PRODUCT(ID)
);

CREATE SEQUENCE order_product_detail_id_seq START WITH 1 INCREMENT BY 1;
```

**欄位說明:**
- `ID`: 主鍵,使用序列 order_product_detail_id_seq
- `ORDER_ID`: 外鍵,關聯到 ORDER_INFO 表
- `PRODUCT_ID`: 外鍵,關聯到 PRODUCT 表
- `QUANTITY`: 訂購數量
- 其他欄位繼承自 BaseEntity

**SQLRestriction**: `DELETED = 0`

## 實施步驟

### 步驟 1: 添加 Flyway 依賴
在 `build.gradle` 中添加:
```gradle
implementation 'org.flywaydb:flyway-core'
runtimeOnly 'org.flywaydb:flyway-database-oracle'
```

### 步驟 2: 創建 Migration 目錄
```
src/main/resources/db/migration/
├── V1__initial_schema.sql
└── V2__initial_data.sql (可選)
```

### 步驟 3: 編寫 V1__initial_schema.sql
包含所有表和序列的創建語句。

### 步驟 4: 更新配置文件

#### application-test.yml
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 改為 validate
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```

#### application-e2e.yml
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 改為 validate
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```

### 步驟 5: 確認 dev 和 openapi 保持原狀
- `application-dev.yml`: 保持 `flyway.enabled: false`
- `application-openapi.yml`: 保持 `flyway.enabled: false`

## 注意事項

1. **H2 相容性**: test 環境使用 H2,需要確保 SQL 語法相容
2. **Baseline 策略**: 使用 `baseline-on-migrate: true` 允許在已有資料的資料庫上執行
3. **驗證模式**: 生產環境使用 `ddl-auto: validate` 確保 schema 與 Entity 一致
4. **序列起始值**: 如果資料庫已有資料,需要調整序列起始值

## 測試計劃

1. **單元測試**: 執行 `./gradlew test` 驗證 test profile
2. **E2E 測試**: 在 Playwright 專案中執行 docker-compose 測試
3. **開發環境**: 確認 dev profile 仍正常運作
4. **文檔生成**: 確認 openapi profile 可正常生成文檔

## 回滾計劃

如果遇到問題,可以:
1. 將 `flyway.enabled` 改回 `false`
2. 將 `ddl-auto` 改回 `create-drop`
3. 刪除 `db/migration` 目錄
