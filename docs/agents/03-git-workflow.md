# Git 工作流程

## 主幹開發（trunk-based）

一人 side project，預設直接在 `main` 小步 commit + push；`main` 沒有 branch protection。CI（`.github/workflows/image-publish.yml`）在 push `main` 與開 PR 時都會跑測試，不需要為了跑測試而開 PR。

- 低風險（小修正、文件、單一 domain 的小改動）→ 直接在 `main` 做。已經在某條工作分支上就延續該分支。
- **高風險** → 開 branch + PR，完整流程走 `high-risk-pr-workflow` skill：
  - 改 CI 本身（`.github/workflows/*`）
  - 改 DB migration（`src/main/resources/db/migration`）
  - 跨 domain 重構或大型功能（多 commit、難一次驗證）
  - 測試抓不到、但壞了影響大的改動

### push `main` 的副作用

push `main` 不只是跑測試，還會：

- 推 `latest` image 到 `ghcr.io`
- `repository-dispatch` 觸發**下游 repo 的 E2E**
- 重新產生 `swagger.json` 推到下游 repo（在 dispatch **之後**）

CI 的 `Run Unit Tests (Gate)` 跑在 build image 之前，所以測試抓得到的錯不會產出壞 image；風險在測試抓不到的執行期／整合問題。

> **改了 API 契約時，下游 job summary 一定會報一次「快照與被測 image 的 spec 有差異」。** 這是 job 順序造成的預期結果：`generate-docs` 是 `needs: build-and-push`，下游 checkout 到的永遠是上一版快照，下一次觸發就恢復。細節見 [09-monitoring.md](./09-monitoring.md#cicd-流程)。

## pre-push hook

`.githooks/pre-push` 在 push 內容含 `main` 時跑與 CI gate 相同的指令：

```bash
./gradlew test -Djunit.platform.exclude.tags=SanityTest
```

push 其他分支時 hook 會略過，測試交給 PR 的 CI。每台機器啟用一次：`git config core.hooksPath .githooks`。只改文件時 test task 是 UP-TO-DATE，數秒就過、不會起 Testcontainers。

## 分支命名

`<前綴>/<任務簡述>`，全小寫、`-` 分隔，從最新 `main` 分出、合併後即刪。前綴：`feature/ fix/ hotfix/ refactor/ config/ docs/ test/ chore/`。範例：`fix/order-creation-transient-entity-bug`。

## Commit 訊息

Conventional Commits：`<type>(<scope>): <subject>` —— 祈使句、小寫開頭、結尾不加句點，寫「做了什麼」。

常用 type：`feat` `fix` `docs` `style` `refactor` `test` `chore`。範例：`fix(product): resolve stock deduction race condition`。

## PR 合併與分支清理

- **合併一律 rebase**（上下游兩個 repo 都只開 rebase merge）：線性歷史、保留每個 commit 的粒度。GitHub MCP 合併時明寫 `merge_method: "rebase"` 並帶 `expectedHeadSha`。
- **PR label** 用 GitHub MCP 的 `issue_write`（`method: "update"`，`issue_number` 填 PR 號）；`create_pull_request` / `update_pull_request` 沒有 labels 欄位。
- **清理**：本 repo 沒開 `delete_branch_on_merge`，合併後遠端分支還在、本地分支也不會變成 `[gone]`。照這三步自己清：

  ```bash
  git push origin --delete <branch>
  git cherry -v main <branch>     # 每行都該是 "-"，代表內容已進 main
  git branch -D <branch>          # rebase 重寫過 SHA，-d 一定會拒絕
  ```

  通用的 git alias 與 FAQ 見 `docs/git-branch-cleanup-guide.md`。

## main 紅了

1. 能快速修好 → 立刻補 `fix:` commit（fix-forward）。
2. 一時修不好 → `git revert <bad-sha>` 先恢復綠燈。
3. `main` 紅著時不疊新的改動。
