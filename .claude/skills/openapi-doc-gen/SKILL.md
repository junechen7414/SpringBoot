---
name: openapi-doc-gen
description: >
  產生 OpenAPI 文件（swagger.json）的完整流程。需要切換到 openapi profile、
  確保 DB 不需要連線（H2 替代）、執行 generateOpenApiDocs task。
  觸發時機：使用者要更新 swagger.json、查看最新 API 文件、
  或 CI 需要重新產生文件時。
---

# OpenAPI Doc Generation

## 背景

本專案透過 `generateOpenApiDocs` Gradle task 產生 `swagger.json`：
- 使用 **`openapi` profile**：切換到 H2 in-memory DB，不需要 Oracle 連線
- `@Profile("openapi")` 的 `SecurityFilterChain` 是全 `permitAll`，讓 `/v3/api-docs` 不被 HTTP Basic 擋住
- 輸出位置：`build/docs/swagger.json`
- CI（push `main` 時）會自動重新產生並推送到下游 repo

---

## 執行步驟

### Step 1：確認不需要 Oracle

`openapi` profile 使用 H2，**不需要啟動 `podman compose`**。若 compose 有在跑，不影響本操作。

### Step 2：執行

```bash
./gradlew generateOpenApiDocs
```

這個 task 會：
1. 以 `openapi` profile 啟動應用（Spring Boot test slice）
2. 打 `/v3/api-docs` 取得 OpenAPI spec
3. 將結果寫入 `build/docs/swagger.json`

### Step 3：驗證輸出

```bash
# 確認檔案存在且非空
cat build/docs/swagger.json | python -m json.tool > $null && echo "Valid JSON"
# 或直接查看
cat build/docs/swagger.json
```

也可在瀏覽器開 Swagger UI 驗證（需 app 正在跑）：
- http://localhost:8787/swagger-ui.html

---

## 常見問題

### `swagger.json` 未更新

task 可能是 UP-TO-DATE 快取。加 `--rerun-tasks` 強制重跑：

```bash
./gradlew generateOpenApiDocs --rerun-tasks
```

### 應用啟動失敗（找不到 datasource）

確認沒有同時指定其他 profile 覆蓋掉 H2 設定。`generateOpenApiDocs` 應自動帶入 `openapi` profile。

### Security 擋住 `/v3/api-docs`

`openapi` profile 有 `@Profile("openapi")` 的全 `permitAll` chain，若被擋住表示 profile 未正確套用。
