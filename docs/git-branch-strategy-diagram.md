# Git 分支策略視覺化

## 📊 分支結構圖

```mermaid
gitGraph
    commit id: "main: 現有提交"
    commit id: "main: 最新狀態"
    
    branch docs/add-rebase-guides
    checkout docs/add-rebase-guides
    commit id: "docs: add rebase guides" tag: "文檔分支"
    
    checkout main
    branch chore/update-config-comments
    checkout chore/update-config-comments
    commit id: "chore: update config comments" tag: "配置分支"
```

---

## 🔄 執行流程圖

```mermaid
flowchart TD
    Start[開始: 本地有未提交的修改] --> Check[檢查當前狀態]
    Check --> Branch1[創建 docs/add-rebase-guides]
    
    Branch1 --> Add1[添加三個 markdown 文件]
    Add1 --> Commit1[提交文檔變更]
    Commit1 --> Push1[推送到 GitHub]
    
    Push1 --> Switch[切換回原分支]
    Switch --> Branch2[創建 chore/update-config-comments]
    
    Branch2 --> Add2[添加配置文件]
    Add2 --> Commit2[提交配置變更]
    Commit2 --> Push2[推送到 GitHub]
    
    Push2 --> Verify[驗證兩個分支都已推送]
    Verify --> PR1[創建 PR: 文檔分支]
    Verify --> PR2[創建 PR: 配置分支]
    
    PR1 --> Review1[等待審查]
    PR2 --> Review2[等待審查]
    
    Review1 --> Merge1[合併到 main]
    Review2 --> Merge2[合併到 main]
    
    Merge1 --> Done[完成]
    Merge2 --> Done
    
    style Start fill:#e1f5ff
    style Branch1 fill:#c8e6c9
    style Branch2 fill:#c8e6c9
    style Push1 fill:#fff9c4
    style Push2 fill:#fff9c4
    style Done fill:#a5d6a7
```

---

## 📁 文件分配圖

```mermaid
flowchart LR
    subgraph Local["本地修改"]
        F1[conflict-resolution-strategy.md]
        F2[rebase-workflow.md]
        F3[rebase-plan.md]
        F4[.gitignore]
        F5[application.yml]
        F6[application-test.yml]
    end
    
    subgraph Branch1["docs/add-rebase-guides"]
        B1F1[conflict-resolution-strategy.md]
        B1F2[rebase-workflow.md]
        B1F3[rebase-plan.md]
    end
    
    subgraph Branch2["chore/update-config-comments"]
        B2F1[.gitignore]
        B2F2[application.yml]
        B2F3[application-test.yml]
    end
    
    F1 --> B1F1
    F2 --> B1F2
    F3 --> B1F3
    
    F4 --> B2F1
    F5 --> B2F2
    F6 --> B2F3
    
    style Branch1 fill:#c8e6c9
    style Branch2 fill:#bbdefb
```

---

## 🎯 分支命名策略

```mermaid
mindmap
  root((Git 分支))
    feature/
      新功能開發
      長期開發
    bugfix/
      Bug 修復
      問題解決
    hotfix/
      緊急修復
      生產問題
    docs/
      文檔更新
      說明文件
      **本次使用**
    chore/
      維護工作
      配置更新
      **本次使用**
    refactor/
      代碼重構
      優化改進
    test/
      測試相關
      測試改進
```

---

## 📝 Commit Message 結構

```mermaid
flowchart TD
    Commit[Commit Message] --> Type[類型前綴]
    Commit --> Subject[簡短摘要]
    Commit --> Body[詳細說明]
    
    Type --> T1[docs:]
    Type --> T2[chore:]
    Type --> T3[feat:]
    Type --> T4[fix:]
    
    Subject --> S1[50 字元以內]
    Subject --> S2[使用祈使語氣]
    Subject --> S3[不加句號]
    
    Body --> B1[空一行]
    Body --> B2[詳細說明變更]
    Body --> B3[列出要點]
    
    style Type fill:#fff9c4
    style Subject fill:#c8e6c9
    style Body fill:#bbdefb
```

---

## 🔄 Pull Request 流程

```mermaid
sequenceDiagram
    participant Dev as 開發者
    participant Local as 本地倉庫
    participant Remote as GitHub
    participant Team as 團隊成員
    
    Dev->>Local: 創建分支
    Dev->>Local: 提交變更
    Dev->>Remote: 推送分支
    Dev->>Remote: 創建 PR
    
    Remote->>Team: 通知審查請求
    Team->>Remote: 審查代碼
    Team->>Remote: 提供反饋
    
    alt 需要修改
        Dev->>Local: 修改代碼
        Dev->>Remote: 推送更新
        Team->>Remote: 再次審查
    end
    
    Team->>Remote: 批准 PR
    Remote->>Remote: 合併到 main
    Remote->>Dev: 通知合併完成
    
    Dev->>Local: 刪除本地分支
    Dev->>Remote: 刪除遠端分支
```

---

## 🎨 分支生命週期

```mermaid
stateDiagram-v2
    [*] --> Created: git checkout -b
    Created --> Modified: 修改文件
    Modified --> Staged: git add
    Staged --> Committed: git commit
    Committed --> Pushed: git push
    Pushed --> PR_Created: 創建 PR
    PR_Created --> Under_Review: 等待審查
    Under_Review --> Approved: 審查通過
    Under_Review --> Changes_Requested: 需要修改
    Changes_Requested --> Modified: 修改代碼
    Approved --> Merged: 合併到 main
    Merged --> Deleted: 刪除分支
    Deleted --> [*]
```

---

## 📊 時間線估算

```mermaid
gantt
    title Git 分支推送時間線
    dateFormat HH:mm
    axisFormat %H:%M
    
    section 準備階段
    檢查狀態           :00:00, 5m
    
    section 文檔分支
    創建分支           :00:05, 1m
    添加文件           :00:06, 2m
    提交變更           :00:08, 2m
    推送到 GitHub      :00:10, 3m
    
    section 配置分支
    切換分支           :00:13, 1m
    創建新分支         :00:14, 1m
    添加文件           :00:15, 2m
    提交變更           :00:17, 2m
    推送到 GitHub      :00:19, 3m
    
    section 後續工作
    驗證推送           :00:22, 3m
    創建 PR (文檔)     :00:25, 5m
    創建 PR (配置)     :00:30, 5m
```

---

## 🛡️ 安全檢查清單

```mermaid
flowchart TD
    Start[開始推送前檢查] --> Check1{檢查敏感資訊}
    Check1 -->|有| Remove[移除敏感資訊]
    Check1 -->|無| Check2{檢查 .gitignore}
    
    Remove --> Check2
    Check2 -->|正確| Check3{檢查 commit message}
    Check2 -->|需更新| Update[更新 .gitignore]
    
    Update --> Check3
    Check3 -->|清晰| Check4{檢查文件內容}
    Check3 -->|需改進| Improve[改進 message]
    
    Improve --> Check4
    Check4 -->|正確| Safe[安全推送]
    Check4 -->|有問題| Fix[修正問題]
    
    Fix --> Check4
    Safe --> Push[執行 git push]
    
    style Safe fill:#c8e6c9
    style Push fill:#a5d6a7
    style Remove fill:#ffcdd2
    style Fix fill:#fff9c4
```

---

## 💡 最佳實踐提醒

### ✅ 應該做的

- 使用描述性的分支名稱
- 寫清晰的 commit message
- 推送前檢查暫存的文件
- 為每個 PR 提供詳細說明
- 定期同步主分支

### ❌ 不應該做的

- 在分支中混合不相關的變更
- 使用模糊的 commit message
- 推送未測試的代碼
- 忽略 code review 反饋
- 直接推送到 main 分支

---

## 🔗 相關文檔

- 詳細執行計劃：[`git-branch-push-plan.md`](git-branch-push-plan.md)
- 快速參考指南：[`git-branch-push-quick-guide.md`](git-branch-push-quick-guide.md)
- Rebase 工作流程：[`rebase-workflow.md`](rebase-workflow.md)
- 衝突解決策略：[`conflict-resolution-strategy.md`](conflict-resolution-strategy.md)

---

**準備好開始了嗎？參考快速指南開始執行！** 🚀