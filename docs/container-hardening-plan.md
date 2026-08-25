# 🛡️ 容器最佳實踐強化計畫

> **建立日期**: 2026-08-25
> **稽核範圍**: `Dockerfile`、`docker-compose.yml`、`.github/workflows/image-publish.yml`、`.env.example`
> **稽核基準**: 三項容器最佳實踐 —— (1) 用 image digest 而非 tag、(2) secrets 寫成 host 檔案並以 volume 掛載（最好在記憶體）、(3) 使用 rootless 容器
> **結論**: 三項皆未遵守；第 3 項僅有「選了 podman」帶來的天然半分

---

## 📋 目錄

1. [稽核結果總表](#稽核結果總表)
2. [現況分析](#現況分析)
3. [待辦（依建議執行順序）](#待辦依建議執行順序)
4. [已經做對、不要誤改的部分](#已經做對不要誤改的部分)
5. [尚未評估的範圍](#尚未評估的範圍)

---

## 稽核結果總表

| 最佳實踐 | 狀態 | 一句話結論 |
|---|---|---|
| Image digest instead of tags | ❌ 未遵守 | 四個第三方服務全用 `:latest`，CI 也以 tag 傳遞給下游 |
| Secrets 以掛載檔案傳遞（最好 in-memory） | ❌ 未遵守 | 走環境變數：`.env` → compose → 容器 env |
| Rootless containers | ⚠️ 半套 | host 端 podman 預設 rootless，但容器內以 root (UID 0) 跑 |

---

## 現況分析

### 1. Image digest — 完全 tag 導向

| 位置 | 現況 | 問題 |
|---|---|---|
| `docker-compose.yml:28` | `container-registry.oracle.com/database/free:latest` | mutable |
| `docker-compose.yml:97` | `grafana/alloy:latest` | mutable |
| `docker-compose.yml:112` | `prom/prometheus:latest` | mutable |
| `docker-compose.yml:131` | `grafana/grafana:latest` | mutable |
| `Dockerfile:3`、`Dockerfile:22` | `eclipse-temurin:25-jdk-alpine` / `25-jre-alpine` | 浮動 tag |

四個第三方服務全用 `:latest` —— mutable tag 裡最糟的一種。今天跑起來的 Oracle 和下週跑起來的可能是不同 image，重現性為零，Oracle 那個影響最大。

CI 端同樣是 tag 導向：`docker/metadata-action` 產出 `latest` / `sha-<short>` / branch name，`image-publish.yml:84` 傳給下游 E2E 的 `image_tag: ${{ steps.meta.outputs.version }}` 是 tag 而非 digest。

同一個 supply-chain pinning 原則在 workflow 自身也沒套用：`actions/checkout@v6`、`docker/login-action@v4`、`docker/build-push-action@v7` 等全是 mutable tag 而非 commit SHA。

### 2. Secrets — 走環境變數

```yaml
# docker-compose.yml:18-19
- SPRING_DATASOURCE_USERNAME=${ORACLE_DEV_USERNAME}
- SPRING_DATASOURCE_PASSWORD=${ORACLE_DEV_PASSWORD}
```

`.env`（磁碟明文）→ compose 展開 → 容器 env。密碼因此出現在 `podman inspect`、容器內 `/proc/<pid>/environ`、所有子行程繼承的環境，以及可能的 crash dump 裡。目前沒有任何 `secrets:`、`tmpfs:`，也沒有 `/run/secrets` 掛載。

### 3. Rootless — 只有 host 端

- **Host / runtime 端**：專案一律用 `podman`，Linux 上預設 rootless，Windows 的 podman machine 內也以非 root 使用者跑。這是選了 podman 帶來的天然優勢，但 repo 內**沒有任何文件或設定明示、驗證或強制**這件事。
- **容器內**：`Dockerfile` 沒有 `USER` 指令，`Dockerfile:45` 的 `ENTRYPOINT ["java", "-jar", "app.jar"]` 以 **root (UID 0)** 執行。這是「Use Rootless Containers」最核心的一項。rootless podman 的 user namespace 對映讓容器內 root ≠ host root，但仍放大容器內攻擊面（可寫任意路徑、可綁 privileged port、逃逸鏈第一步更好走）。
- 第三方 image 的預設 user 不在本 repo 控制範圍內，可用 compose 的 `user:` 或 `userns_mode:` 收斂。

---

## 待辦（依建議執行順序）

### 1. Dockerfile 加 `USER`（最高優先，風險最低）

runtime stage 補三行即可：

```dockerfile
RUN addgroup -S app && adduser -S -G app app && chown -R app:app /app
USER app
```

單一 commit、無跨檔案影響，收益最直接。

### 2. `docker-compose.yml` 四個 `:latest` 改 digest pin

`Dockerfile` 的兩個 `eclipse-temurin` 浮動 tag 一併處理。與第 1 項可合併為同一個 commit 進 `main`。

### 3. CI 傳 digest 給下游 E2E

`docker/build-push-action` 有 `outputs.digest`（需先給該 step 一個 `id`），用它取代 `image-publish.yml:84` 的 `steps.meta.outputs.version`。順帶消掉「下游快照落後一版」那類時序不確定性（見 `docs/agents/09-monitoring.md`）。

> ⚠️ 動到 CI workflow ＝ 高風險變更，依 `CLAUDE.md` 慣例走 branch + PR（`high-risk-pr-workflow` skill）。

### 4. Secrets 改 configtree + file secret

Spring Boot 原生支援 config tree，**不需改任何 Java 程式碼**：

```yaml
# application.yml
spring.config.import: optional:configtree:/run/secrets/
```

```yaml
# docker-compose.yml
secrets:
  spring_datasource_password:
    file: ./secrets/spring.datasource.password   # 檔名即 property key
```

podman / docker compose 的 file secret 會掛進容器的 `/run/secrets`，該掛載點本身是 tmpfs，符合「preferably in-memory」。host 端那份檔案仍在磁碟上 —— 真要 in-memory 得把 `./secrets` 放在 host 的 tmpfs / ramdisk，或改接外部 secret manager。

> ⚠️ 牽涉 `e2e` profile 與下游 `docker-compose.test.yml` 的環境變數契約（見 `docs/testing-architecture-overview.md`），屬跨環境變更，建議走 branch + PR。

### 5. workflow 的 actions 改 commit SHA pin

`actions/checkout@v6` 這類 mutable tag 全部改為 commit SHA，與第 3 項同批處理。

---

## 已經做對、不要誤改的部分

- `.env` 已列入 `.gitignore`，git 未追蹤（只有 `.env.example` 在版控內）。
- actuator exposure 只開 `health,info,metrics`（`application.yml`），**沒有** `/actuator/env` 或 `configprops` —— 少一條環境變數洩漏管道。修 secrets 時不要為了除錯把它們打開。

---

## 尚未評估的範圍

本次稽核只針對開頭列的三項最佳實踐。以下 hardening 選項**尚未評估**：

- `read_only: true`（唯讀根檔案系統）
- `cap_drop`（丟棄不需要的 Linux capabilities）
- `security_opt: no-new-privileges`
- 第三方 image 的 `user:` / `userns_mode:` 收斂
