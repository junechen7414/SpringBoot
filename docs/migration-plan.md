# 測試環境遷移至 Oracle DB 實施計劃

## 📋 任務概述
將測試環境從 H2 內存資料庫遷移至 Oracle 資料庫，使用獨立的測試環境連接配置。

## 🔍 當前狀態分析

### 現有配置
- **Dev 環境** (`application-dev.yml`):
  - 資料庫: Oracle DB
  - 連接: `jdbc:oracle:thin:@//oracle-db:1521/FREEPDB1`
  - 認證: `${ORACLE_DEV_USERNAME}` / `${ORACLE_DEV_PASSWORD}`
  - DDL 策略: `create-drop`

- **Test 環境** (`application-test.yml`):
  - 資料庫: H2 內存資料庫
  - 連接: `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=Oracle`
  - Dialect: `H2Dialect`
  - DDL 策略: `create-drop`

### 依賴分析 (`build.gradle`)
```gradle
runtimeOnly 'com.oracle.database.jdbc:ojdbc11'  ✅ Oracle JDBC 驅動已存在
runtimeOnly 'com.h2database:h2'                  ⚠️ 可保留作為備選
testImplementation 'com.h2database:h2'           ⚠️ 遷移後可能不需要
```

## 📝 實施步驟

### 1. 修改測試環境配置文件
**文件**: `src/test/resources/application-test.yml`

**變更內容**:
```yaml
# 從 H2 配置
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=Oracle
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop

# 改為 Oracle 配置
spring:
  datasource:
    url: jdbc:oracle:thin:@//oracle-db:1521/FREEPDB1
    username: ${ORACLE_TEST_USERNAME}
    password: ${ORACLE_TEST_PASSWORD}
    driver-class-name: oracle.jdbc.driver.OracleDriver
    hikari:
      connection-timeout: 5000
      maximum-pool-size: 20
  jpa:
    hibernate:
      ddl-auto: create-drop
```

**關鍵變更點**:
- ✅ 使用 Oracle JDBC URL
- ✅ 使用獨立的測試環境變數 (`ORACLE_TEST_USERNAME`, `ORACLE_TEST_PASSWORD`)
- ✅ 移除 H2 特定的 `database-platform` 配置
- ✅ 添加 Hikari 連接池配置（與 dev 環境一致）
- ✅ 保持 `create-drop` 策略確保測試隔離性

### 2. 更新環境變數範例文件
**文件**: `.env.example`

**新增內容**:
```env
# 開發環境 Oracle 資料庫
ORACLE_DEV_USERNAME=your_dev_username
ORACLE_DEV_PASSWORD=your_dev_password

# 測試環境 Oracle 資料庫
ORACLE_TEST_USERNAME=your_test_username
ORACLE_TEST_PASSWORD=your_test_password
```

### 3. Gradle 依賴調整（可選）
**文件**: `build.gradle`

**選項 A - 保持現狀**（推薦）:
- 保留 H2 依賴，以便未來需要時可快速切換回來
- 不需要修改 `build.gradle`

**選項 B - 清理 H2 依賴**:
```gradle
// 移除或註解掉
// testImplementation 'com.h2database:h2'
```

## ⚠️ 注意事項

### 環境變數設置
測試執行前必須設置以下環境變數：
```bash
# Windows (PowerShell)
$env:ORACLE_TEST_USERNAME="test_user"
$env:ORACLE_TEST_PASSWORD="test_password"

# Linux/Mac
export ORACLE_TEST_USERNAME=test_user
export ORACLE_TEST_PASSWORD=test_password
```

### 測試資料庫要求
1. **獨立測試 Schema**: 建議使用與開發環境不同的 Oracle schema/用戶
2. **權限要求**: 測試用戶需要 CREATE/DROP TABLE 權限（因為使用 `create-drop`）
3. **資料隔離**: 確保測試資料庫與開發/生產環境完全隔離

### CI/CD 考量
如果使用 CI/CD 管道：
1. 需要在 CI 環境中配置 `ORACLE_TEST_USERNAME` 和 `ORACLE_TEST_PASSWORD`
2. 確保 CI 環境可以訪問 Oracle 測試資料庫
3. 考慮使用 Docker 容器運行 Oracle 測試實例

### 潛在影響
1. **測試執行時間**: Oracle 資料庫可能比 H2 內存資料庫慢
2. **網絡依賴**: 測試需要網絡連接到 Oracle 資料庫
3. **並發測試**: 多個測試同時運行可能需要不同的 schema 或連接池配置

## 🔄 回滾計劃
如果遷移後出現問題，可以快速回滾：

1. 恢復 `application-test.yml` 為原始 H2 配置
2. 確保 H2 依賴仍在 `build.gradle` 中
3. 移除測試環境變數

## ✅ 驗證步驟

### 1. 配置驗證
```bash
# 檢查環境變數是否設置
echo $ORACLE_TEST_USERNAME  # Linux/Mac
echo $env:ORACLE_TEST_USERNAME  # Windows PowerShell
```

### 2. 連接測試
運行簡單的測試確認資料庫連接：
```bash
./gradlew test --tests DemoApplicationTests
```

### 3. 完整測試套件
```bash
./gradlew test
```

### 4. 檢查測試日誌
確認日誌中顯示正確的資料庫連接：
- 應該看到 Oracle JDBC 驅動初始化
- 應該看到連接到 `oracle-db:1521/FREEPDB1`
- 不應該看到 H2 相關的日誌

## 📊 預期結果

✅ 測試環境成功連接到 Oracle 資料庫  
✅ 所有現有測試通過  
✅ 測試資料在每次測試後正確清理（create-drop）  
✅ 測試執行時間在可接受範圍內  
✅ 環境變數正確讀取和使用  

## 🚀 後續建議

1. **性能監控**: 比較遷移前後的測試執行時間
2. **文檔更新**: 更新專案 README，說明測試環境配置要求
3. **Docker Compose**: 考慮在 `docker-compose.yml` 中添加測試用 Oracle 容器
4. **測試資料管理**: 建立測試資料初始化腳本，確保測試環境一致性

## 📚 相關文件

- `src/main/resources/application-dev.yml` - 開發環境配置參考
- `src/test/resources/application-test.yml` - 測試環境配置（需修改）
- `.env.example` - 環境變數範例（需更新）
- `build.gradle` - 依賴管理
- `docker-compose.yml` - 可能需要添加測試資料庫服務
