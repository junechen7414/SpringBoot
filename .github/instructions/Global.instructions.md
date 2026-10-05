---
name: instructions
description: Every conversation
---

@../../docs/agents/01-overview.md
@../../docs/agents/03-git-workflow.md
@../../docs/agents/05-code-standards.md
@../../docs/agents/06-architecture.md
@../../docs/agents/08-testing.md

## 何時讀哪份文件

以下文件不會常駐載入，遇到對應情境時**先讀再動手**：

- **環境**：啟動本地環境、建立或升級 podman machine／WSL、設 `.env`、新增或修改 Spring profile 與 `application-*.yml` 的 datasource 或檔案位置、呼叫受 Basic 認證保護的 API、升級 Java 版本、改 `Dockerfile`（內建 `HEALTHCHECK` 是下游 E2E 依賴的契約）、用 podman 建置要發佈的映像 → `docs/agents/02-setup.md`
- **監控**：動 `build.gradle` 的依賴、`application*.yml` 的 `management.*`、`config.alloy`、`tempo.yaml`、`prometheus.yml`、`grafana/**`、compose 的監控服務、或 CI workflow 的 job 順序 → `docs/agents/09-monitoring.md`
- **疑難排解**：容器 `healthy` 但宿主 `localhost` 拒絕連線、`podman machine ssh` 留下 `NUL` 檔、樂觀鎖衝突的回應格式，或想知道 RestClient／Resilience4j／`@Embedded` 為何這樣選 → `docs/agents/10-troubleshooting.md`
- **AI 工具設定**：新增或修改 MCP、skill、subagent／Bob mode，或調整本檔與 `AGENTS.md` 的 import 清單 → `docs/agents/11-ai-tools-overview.md`
