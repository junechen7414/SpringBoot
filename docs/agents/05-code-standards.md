## 程式碼設計原則

### 核心原則

1. **可讀性優先**: 即使程式碼簡短或執行快速，若難以理解則不採用
2. **現代化語法**: 優先使用 Java 21 新特性（如 Virtual Threads, Pattern Matching）
3. **實務導向**: 理論正確但實務不適用的方案應避免

### 文檔管理

所有專案文檔必須放置於 `docs/` 目錄，命名遵循 kebab-case：
- 指南類: `*-guide.md`
- 計劃類: `*-plan.md`
- 說明類: `*-explanation.md`
- 流程圖: `*-workflow.md` 或 `*-diagram.md`

### Agent 文件維護規範

#### 文件同步更新原則

1. **跨 Agent 文件同步**: 當對話過程中更新任何 Agent 相關文件時，必須確保所有類型的 Agent 文件都同步更新，包括：
   - `AGENTS.md` (Cline、Bob、Gemini CLI 等通用 Agent)
   - `.github/instructions/Global.instructions.md` (GitHub Copilot)
   - 其他專案特定的 Agent 配置檔案

2. **文件過時檢測**: 在對話開始前、進行中或結束後，若發現文件內容已經過時或不符合實際情況，應：
   - 立即標記過時的內容
   - 提出更新建議
   - 在獲得確認後同步更新所有相關 Agent 文件
   - 記錄更新日期與變更原因

3. **一致性驗證**: 定期檢查各 Agent 文件間的規範是否一致，特別是：
   - 語言偏好設定
   - 工具使用規範（如 podman vs docker）
   - 程式碼風格與架構原則
   - Git 工作流程規範

4. **更新觸發時機**:
   - 專案架構或技術棧變更時
   - 開發規範或最佳實踐更新時
   - 發現文件與實際情況不符時
   - 新增或移除開發工具時
