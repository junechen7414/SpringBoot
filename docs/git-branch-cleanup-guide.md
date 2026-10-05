# Git 分支清理指南

> **本 repo 實際用的流程在 [`docs/agents/03-git-workflow.md`](./agents/03-git-workflow.md)「PR 合併與分支清理」**：先刪遠端、`git cherry` 確認、再 `git branch -D`。
> 本 repo 沒開 `delete_branch_on_merge`，合併本身不會讓本地分支變成 `[gone]`，所以下面以 `[gone]` 為準的 alias 只會在你手動刪掉遠端分支之後才清得到東西。哪天在 repo Settings 打開該選項，這份指南的主線才會直接生效。

## `[gone]` 分支是什麼

遠端分支被刪除後，`git fetch --prune` 會讓對應的本地分支顯示成 `[gone]`：

```
* main                 f319b2d [origin/main]
  feature/add-payment  abc1234 [origin/feature/add-payment: gone]
```

## 一次性設定 alias

alias 寫在全域 `~/.gitconfig`，**Git Bash 版和 PowerShell 版不能混用**：裝了其中一版，在另一個 shell 執行會失敗（例如 Git Bash 沒有 `Select-String`）。用 `git config --global --get-regexp alias` 查目前裝的是哪一版；需要跨 shell 時改用下方的手動指令。

**Git Bash：**

```bash
git config --global alias.show-gone "!git fetch --prune && git branch -vv | grep ': gone]'"
git config --global alias.prune-local "!git fetch --prune && git branch -vv | grep ': gone]' | awk '{print \$1}' | xargs -r git branch -D"
git config --global alias.cleanup "!git branch --merged main | grep -v '\\*\\|main\\|master' | xargs -r git branch -d"
```

**PowerShell：**

```powershell
git config --global alias.show-gone "!git fetch --prune; git branch -vv | Select-String ': gone]'"
git config --global alias.prune-local "!git fetch --prune; git branch -vv | Select-String ': gone]' | ForEach-Object { `$_.Line.Trim().Split()[0] } | ForEach-Object { git branch -D `$_ }"
git config --global alias.cleanup "!git branch --merged main | Select-String -NotMatch '\\*|main|master' | ForEach-Object { `$_.Line.Trim() } | ForEach-Object { git branch -d `$_ }"
```

- `show-gone`：只列出 `[gone]` 分支，不刪。
- `prune-local`：刪除所有 `[gone]` 分支。用 `-D` 是因為遠端已經刪了。
- `cleanup`：刪除已經是 `main` 祖先的分支。**rebase merge 的分支不會被它刪到**，因為 SHA 已被重寫。

移除 alias：`git config --global --unset alias.<name>`。

## 手動指令（兩種 shell 都能用）

```bash
git fetch --prune
git branch -vv | grep ': gone]'                                          # 查看
git branch -vv | grep ': gone]' | awk '{print $1}' | xargs -r git branch -D   # 全刪
```

## 常見問題

- **刪本地分支會影響遠端嗎？** 不會。
- **誤刪了怎麼辦？** `git reflog` 找到最後的 commit，再 `git checkout -b <name> <sha>`。
- **`-d` 和 `-D` 差在哪？** `-d` 只刪已經合併成祖先的分支；`-D` 強制刪除。在本 repo 強刪前，先用 `git cherry -v main <branch>` 確認每行都是 `-`。
