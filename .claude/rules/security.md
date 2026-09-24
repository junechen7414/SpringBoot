---
paths:
  - "src/main/java/com/ibm/demo/config/SecurityConfig.java"
  - "src/main/java/com/ibm/demo/config/RestClientConfig.java"
---

# Security 細節

總則見 `CLAUDE.md`「Security」。以下是修改安全設定或 `*Client` 傳輸時才需要的細節。

- `*Client` 自呼叫透過 loopback 繞回，`RestClientConfig` 掛 `internal` 帳號憑證。
- 使用者是兩個 **in-memory 機器帳號**，密碼以 `{noop}` 逐字比對（不雜湊，理由見 `SecurityConfig` 註解）；`roles` 保留但無規則使用。
- 正式環境應改為只當 OAuth2 Resource Server、authN/authZ 外包給 IdP — 見 `docs/security-external-idp-migration.md`。
