# 衝突解決策略 - 用戶主導模式

## 核心原則

**遇到任何衝突時，立即暫停並詢問用戶如何處理**

---

## 衝突處理流程

### 1. 發現衝突時

當執行 `git rebase main` 遇到衝突時，系統會：

```
CONFLICT (content): Merge conflict in <filename>
error: could not apply <commit>... <commit message>
hint: Resolve all conflicts manually, mark them as resolved with
hint: "git add/rm <conflicted_files>", then run "git rebase --continue".
```

### 2. 暫停並報告

立即執行以下命令查看衝突狀態：

```bash
# 查看哪些檔案有衝突
git status

# 查看衝突的具體內容
git diff
```

### 3. 向用戶報告

報告內容包括：
- 衝突的檔案名稱
- 衝突的具體位置
- 兩個版本的差異內容
- 等待用戶指示下一步

### 4. 等待用戶決策

用戶可以選擇以下任一方式：

#### 選項 A: 手動編輯解決
```bash
# 用編輯器打開衝突檔案
code <conflicted-file>

# 手動編輯，移除衝突標記並保留想要的內容
# 完成後標記為已解決
git add <conflicted-file>
git rebase --continue
```

#### 選項 B: 保留 add-flyway 的版本
```bash
git checkout --ours <conflicted-file>
git add <conflicted-file>
git rebase --continue
```

#### 選項 C: 保留 main 的版本
```bash
git checkout --theirs <conflicted-file>
git add <conflicted-file>
git rebase --continue
```

#### 選項 D: 使用合併工具
```bash
git mergetool
# 使用圖形化工具解決衝突
git rebase --continue
```

#### 選項 E: 中止 rebase
```bash
git rebase --abort
# 回到 rebase 前的狀態
```

---

## 衝突標記格式

衝突會以以下格式顯示在檔案中：

```
<<<<<<< HEAD (main)
這是 main 分支的內容
=======
這是 add-flyway 分支的內容
>>>>>>> add-flyway
```

**解釋**：
- `<<<<<<< HEAD (main)` 到 `=======` 之間：main 分支的內容
- `=======` 到 `>>>>>>> add-flyway` 之間：add-flyway 分支的內容
- 用戶需要決定保留哪些內容，並移除這些標記

---

## 可能出現衝突的檔案

根據 `add-flyway` 分支的變更分析，最可能出現衝突的檔案：

### 1. build.gradle
**衝突原因**：如果 main 也新增了其他依賴

**add-flyway 的變更**：
```gradle
// Flyway 用於版本化資料庫遷移，保護生產資料安全
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-oracle'
```

**處理建議**：通常需要合併兩邊的依賴，確保 Flyway 依賴被保留

---

### 2. application.yml
**衝突原因**：如果 main 修改了 JPA 或其他相同的配置區塊

**add-flyway 的變更**：
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 從 create-drop 改為 validate
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration/oracle
    sql-migration-prefix: V
    sql-migration-separator: __
    sql-migration-suffixes: .sql
```

**處理建議**：
- 確保 `ddl-auto: validate` 被保留
- 確保完整的 Flyway 配置區塊存在
- 如果 main 有其他新配置，也要保留

---

### 3. 筆記.md
**衝突原因**：如果 main 也新增了其他章節

**add-flyway 的變更**：
- 新增「8. Flyway 數據庫遷移」章節（約 60 行）

**處理建議**：
- 保留 main 的所有內容
- 將 Flyway 章節附加到適當位置（通常是文件末尾）

---

### 4. application-test.yml
**衝突原因**：如果 main 修改了測試配置

**add-flyway 的變更**：
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 從 create-drop 改為 validate
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration/h2
    sql-migration-prefix: V
    sql-migration-separator: __
    sql-migration-suffixes: .sql
    placeholderReplacement: false
```

**處理建議**：確保測試環境的 Flyway 配置正確指向 H2 migration

---

## 衝突解決檢查清單

解決每個衝突後，確認：

- [ ] 所有衝突標記（`<<<<<<<`, `=======`, `>>>>>>>`）都已移除
- [ ] 檔案語法正確（沒有語法錯誤）
- [ ] 保留了必要的 Flyway 相關變更
- [ ] 保留了 main 分支的重要變更
- [ ] 使用 `git add <file>` 標記為已解決
- [ ] 準備好執行 `git rebase --continue`

---

## 實際操作範例

### 範例：解決 build.gradle 衝突

**步驟 1：查看衝突**
```bash
git status
# 顯示：both modified: build.gradle

cat build.gradle
# 或使用編輯器打開
```

**步驟 2：檔案內容可能如下**
```gradle
dependencies {
    // ... 其他依賴 ...
    
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
}
```

**步驟 3：詢問用戶**
"發現 build.gradle 衝突：
- main 新增了 spring-boot-starter-security
- add-flyway 新增了 Flyway 依賴和 testImplementation h2

請問要如何處理？
A) 保留兩邊的變更（合併）
B) 只保留 main 的版本
C) 只保留 add-flyway 的版本
D) 讓我手動編輯"

**步驟 4：根據用戶選擇執行**

如果用戶選擇 A（合併），編輯為：
```gradle
dependencies {
    // ... 其他依賴 ...
    
    implementation 'org.springframework.boot:spring-boot-starter-security'
    runtimeOnly 'com.h2database:h2'
    testImplementation 'com.h2database:h2'
    // Flyway 用於版本化資料庫遷移，保護生產資料安全
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-oracle'
}
```

**步驟 5：標記已解決並繼續**
```bash
git add build.gradle
git rebase --continue
```

---

## 緊急情況處理

### 如果不確定如何解決
```bash
# 暫時中止 rebase
git rebase --abort

# 回到初始狀態，重新規劃
```

### 如果解決錯誤
```bash
# 如果還在 rebase 過程中
git rebase --abort

# 如果已經完成 rebase
git reflog
git reset --hard HEAD@{n}  # n 是 rebase 前的位置
```

---

## 總結

**核心原則再次強調**：
1. 遇到衝突立即暫停
2. 詳細報告衝突情況
3. 等待用戶明確指示
4. 不自行決定保留哪些內容
5. 用戶解決後才繼續

這確保了：
- 用戶完全掌控衝突解決過程
- 不會意外丟失重要變更
- 可以根據實際情況做出最佳決策