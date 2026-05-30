## 程式碼修改影響範圍檢查

**核心原則**: 修改任何層級的程式碼時，必須檢查所有相依層級是否需要同步更新。

### 跨層相依性檢查清單

**修改 Service 層時**，必須檢查：
1. ✅ **Controller 層**: 方法簽名、參數傳遞、import 語句
2. ✅ **Client 層**: 若 Service 被其他模組呼叫，檢查 Client 介面與實作
3. ✅ **DTO 層**: 新增或修改的 DTO 是否在所有使用處都正確 import
4. ✅ **測試層**: 單元測試與整合測試是否需要更新

**修改 Controller 層時**，必須檢查：
1. ✅ **Service 層**: 方法簽名是否匹配
2. ✅ **DTO 層**: Request/Response DTO 是否正確使用與 import
3. ✅ **API 文件**: OpenAPI 註解是否需要更新

**修改 Client 層時**，必須檢查：
1. ✅ **Service 層**: 呼叫 Client 的 Service 是否需要更新
2. ✅ **DTO 層**: 跨模組傳遞的 DTO 是否正確 import
3. ✅ **RestClient 配置**: 是否需要更新 URL 或錯誤處理

**修改 Repository 層時**，必須檢查：
1. ✅ **Service 層**: 查詢方法的呼叫是否需要調整
2. ✅ **Entity 層**: 實體欄位變更是否影響查詢

**修改 Entity 層時**，必須檢查：
1. ✅ **Repository 層**: 自定義查詢是否需要更新
2. ✅ **DTO 層**: 映射邏輯是否需要調整
3. ✅ **資料庫遷移**: 是否需要新增 Flyway 腳本

**修改 DTO 層時**，必須檢查：
1. ✅ **Controller 層**: Request/Response 使用處
2. ✅ **Service 層**: DTO 轉換邏輯
3. ✅ **Client 層**: 跨模組呼叫的參數與回傳值

### Agent 執行流程

**核心原則**: 發現問題或多步驟任務時，**立即建立待辦清單**，避免因上下文限制而遺漏。

**工作流程**:
```
發現問題/任務 → 立即建立待辦清單 → 逐步完成 → 更新狀態 → 驗證完成
```

**修改程式碼後的檢查流程**:

1. **建立待辦清單** (若任務涉及多個步驟):
   ```
   使用 update_todo_list 建立清單，例如：
   [ ] 識別修改層級
   [ ] 搜尋相依檔案
   [ ] 更新相依檔案
   [ ] 編譯驗證
   [ ] 測試驗證
   ```

2. **識別修改層級**: 判斷修改的檔案屬於哪一層（Controller/Service/Client/Repository/Entity/DTO）

3. **列出相依檔案**: 使用 `search_file_content` 搜尋所有引用該類別的檔案

4. **逐一檢查**: 確認每個相依檔案是否需要更新（import、方法呼叫、參數傳遞）

5. **編譯驗證**: 執行 `./gradlew compileJava` 確認無編譯錯誤

6. **測試驗證**: 執行相關測試確認功能正常

7. **更新待辦清單**: 完成每個步驟後標記為完成

**何時建立待辦清單**:
- ✅ 發現編譯錯誤需要修復多個檔案
- ✅ 任務涉及 3 個以上步驟
- ✅ 需要跨層級修改程式碼
- ✅ 需要同步更新多個配置檔案
- ✅ 複雜的重構或功能開發

**待辦清單範例**:
```markdown
[ ] 修復 ProductService.java import 錯誤
[ ] 修復 ProductController.java import 錯誤
[ ] 執行編譯驗證
[ ] 更新 AGENTS.md 加入跨層檢查規範
[ ] 更新 Global.instructions.md 同步規範
[ ] 執行測試驗證
```

**範例：修改 Service 層方法簽名**

```bash
# 1. 搜尋所有引用該 Service 的檔案
search_file_content("ProductService", "src/main/java")

# 2. 檢查 Controller 是否需要更新
read_file("src/main/java/.../ProductController.java")

# 3. 檢查 Client 是否需要更新（若存在）
read_file("src/main/java/.../ProductClient.java")

# 4. 檢查是否有新的 DTO 需要 import
search_file_content("ProcessOrderItemsRequest", "src/main/java")

# 5. 編譯驗證
execute_command("./gradlew compileJava")

# 6. 執行測試
execute_command("./gradlew test --tests ProductServiceTest")
```

**常見遺漏場景**：
- ❌ 修改 Service 方法參數，忘記更新 Controller 的 import
- ❌ 新增 DTO 類別，忘記在 Controller 或 Client 中 import
- ❌ 修改 Repository 查詢方法，忘記更新 Service 的呼叫
- ❌ 變更 Entity 欄位，忘記更新 Flyway 遷移腳本
- ❌ 修改 Service 介面，忘記更新對應的 Client 實作

**預防措施**：
1. 修改前使用 `search_file_content` 找出所有引用
2. 修改後執行 `compileJava` 確認無編譯錯誤
3. 執行相關測試確認功能正常
4. Code Review 時特別注意跨層相依性
5. 使用 IDE 的「Find Usages」功能輔助檢查
