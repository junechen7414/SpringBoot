# Git Rebase 執行計劃：更新 add-flyway 分支

## 目標
將 `add-flyway` 分支更新到與 `main` 分支同步，同時保留所有 Flyway 相關的 commit。

## 當前狀態分析

### 分支信息
- **當前分支**: `add-flyway`
- **目標分支**: `main`
- **策略**: Git Rebase（線性歷史）

### add-flyway 分支的變更內容
根據 `git diff main` 的結果，`add-flyway` 分支包含以下 Flyway 相關變更：

1. **build.gradle**
   - 新增 Flyway 依賴：`flyway-core` 和 `flyway-database-oracle`

2. **application.yml**
   - 將 `ddl-auto` 從 `create-drop` 改為 `validate`
   - 新增完整的 Flyway 配置區塊

3. **新增 Migration 檔案**
   - `src/main/resources/db/migration/h2/V1__initial_schema.sql` (H2 測試環境)
   - `src/main/resources/db/migration/oracle/V1__initial_schema.sql` (Oracle 生產環境)

4. **新增測試檔案**
   - `src/test/java/com/ibm/demo/FlywayMigrationTests.java`

5. **application-test.yml**
   - 將測試環境的 `ddl-auto` 改為 `validate`
   - 新增 Flyway 測試配置

6. **筆記.md**
   - 新增 Flyway 數據庫遷移章節的完整文檔

## 執行步驟

### 步驟 1: 檢查工作目錄狀態
```bash
git status
```
**目的**: 確保沒有未提交的變更，避免 rebase 過程中的衝突。

**預期結果**: 
- 如果有未提交變更 → 先 commit 或 stash
- 如果乾淨 → 繼續下一步

---

### 步驟 2: 獲取最新的 main 分支
```bash
git fetch origin main
```
**目的**: 確保本地的 main 分支引用是最新的。

---

### 步驟 3: 查看分支差異
```bash
git log --oneline --graph --decorate main..add-flyway
```
**目的**: 查看 `add-flyway` 有哪些 commit 需要重新應用。

```bash
git log --oneline --graph --decorate add-flyway..main
```
**目的**: 查看 `main` 有哪些新的 commit 是 `add-flyway` 沒有的。

---

### 步驟 4: 執行 Rebase
```bash
git rebase main
```
**目的**: 將 `add-flyway` 的 commit 重新應用到最新的 `main` 之上。

**可能的結果**:

#### 情況 A: Rebase 成功（無衝突）
```
Successfully rebased and updated refs/heads/add-flyway.
```
→ 跳到步驟 6

#### 情況 B: 出現衝突
```
CONFLICT (content): Merge conflict in <file>
```
→ 繼續步驟 5

---

### 步驟 5: 處理衝突（如果需要）

#### 5.1 查看衝突檔案
```bash
git status
```
會顯示哪些檔案有衝突（標記為 `both modified`）

#### 5.2 可能的衝突檔案
根據變更內容分析，最可能出現衝突的檔案：
- `build.gradle` - 如果 main 也新增了其他依賴
- `application.yml` - 如果 main 修改了相同的配置區塊
- `筆記.md` - 如果 main 也新增了其他章節

#### 5.3 解決衝突策略

**重要：遇到任何衝突時，立即暫停並詢問用戶如何處理**

當出現衝突時：
1. 使用 `git status` 查看衝突檔案
2. 使用 `git diff` 查看衝突內容
3. **暫停操作，向用戶報告衝突情況**
4. 等待用戶指示如何解決
5. 不要自行決定保留哪些內容

用戶可能的選擇：
- 手動編輯檔案解決衝突
- 使用 `git checkout --ours <file>` 保留 add-flyway 的版本
- 使用 `git checkout --theirs <file>` 保留 main 的版本
- 使用合併工具 `git mergetool`
- 中止 rebase `git rebase --abort`

#### 5.4 標記衝突已解決（用戶解決後）
```bash
git add <resolved-file>
```

#### 5.5 繼續 Rebase
```bash
git rebase --continue
```

#### 5.6 如果需要中止 Rebase
```bash
git rebase --abort
```
這會回到 rebase 前的狀態。

---

### 步驟 6: 驗證 Rebase 結果

#### 6.1 檢查 commit 歷史
```bash
git log --oneline --graph --decorate -10
```
**驗證**: 
- add-flyway 的 commit 應該在 main 的最新 commit 之後
- commit 歷史應該是線性的

#### 6.2 檢查檔案完整性
```bash
git diff main
```
**驗證**: 應該只顯示 Flyway 相關的變更（與原始 diff 相同）

#### 6.3 確認關鍵檔案存在
```bash
ls -la src/main/resources/db/migration/h2/
ls -la src/main/resources/db/migration/oracle/
ls -la src/test/java/com/ibm/demo/FlywayMigrationTests.java
```

---

### 步驟 7: 推送到遠端（可選）

**注意**: Rebase 會改寫 commit 歷史，如果 `add-flyway` 已經推送到遠端，需要使用 force push。

```bash
# 如果分支尚未推送
git push origin add-flyway

# 如果分支已存在於遠端（需要 force push）
git push origin add-flyway --force-with-lease
```

**`--force-with-lease` 的優點**:
- 比 `--force` 更安全
- 如果遠端有其他人的 commit，會拒絕推送
- 避免意外覆蓋他人的工作

---

## 衝突解決範例

### 範例 1: build.gradle 衝突

**衝突內容**:
```gradle
<<<<<<< HEAD (main)
	implementation 'org.springframework.boot:spring-boot-starter-security'
	runtimeOnly 'com.h2database:h2'
=======
	runtimeOnly 'com.h2database:h2'
	testImplementation 'com.h2database:h2'
	// Flyway 用於版本化資料庫遷移，保護生產資料安全
	implementation 'org.flywaydb:flyway-core'
	implementation 'org.flywaydb:flyway-database-oracle'
>>>>>>> add-flyway
```

**解決後**:
```gradle
	implementation 'org.springframework.boot:spring-boot-starter-security'
	runtimeOnly 'com.h2database:h2'
	testImplementation 'com.h2database:h2'
	// Flyway 用於版本化資料庫遷移，保護生產資料安全
	implementation 'org.flywaydb:flyway-core'
	implementation 'org.flywaydb:flyway-database-oracle'
```

### 範例 2: application.yml 衝突

**衝突內容**:
```yaml
<<<<<<< HEAD (main)
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
=======
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    baseline-on-migrate: true
>>>>>>> add-flyway
```

**解決後**:
```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration/oracle
    sql-migration-prefix: V
    sql-migration-separator: __
    sql-migration-suffixes: .sql
```

---

## 回滾計劃

如果 rebase 過程出現問題，可以使用以下方式回滾：

### 方法 1: 使用 reflog
```bash
# 查看最近的操作
git reflog

# 找到 rebase 前的 commit（例如 HEAD@{1}）
git reset --hard HEAD@{1}
```

### 方法 2: 使用備份分支
在 rebase 前創建備份：
```bash
git branch add-flyway-backup
```

如需回滾：
```bash
git reset --hard add-flyway-backup
```

---

## 檢查清單

執行 rebase 後，請確認以下項目：

- [ ] Git 歷史是線性的（無 merge commit）
- [ ] 所有 Flyway 相關檔案都存在
- [ ] `build.gradle` 包含 Flyway 依賴
- [ ] `application.yml` 的 `ddl-auto` 設為 `validate`
- [ ] `application.yml` 包含完整的 Flyway 配置
- [ ] Migration 檔案存在於正確的目錄
- [ ] 測試檔案 `FlywayMigrationTests.java` 存在
- [ ] `application-test.yml` 包含測試環境的 Flyway 配置
- [ ] `筆記.md` 包含 Flyway 章節
- [ ] `git diff main` 只顯示 Flyway 相關變更

---

## 預期時間

- 無衝突情況：5-10 分鐘
- 有衝突情況：15-30 分鐘（取決於衝突複雜度）

---

## 注意事項

1. **不要在公共分支上 rebase**: 如果其他人也在使用 `add-flyway` 分支，應該使用 merge 而非 rebase
2. **備份很重要**: 在執行 rebase 前建議創建備份分支
3. **逐步驗證**: 每解決一個衝突後，確認檔案內容正確再繼續
4. **測試驗證**: Rebase 完成後，建議執行測試確保功能正常

---

## 後續步驟

Rebase 完成後：

1. **本地測試**: 執行 `./gradlew test` 確保所有測試通過
2. **啟動應用**: 確認 Flyway migration 正常執行
3. **Code Review**: 如果需要，請團隊成員 review 變更
4. **合併到 main**: 當準備好時，可以將 `add-flyway` 合併到 `main`
