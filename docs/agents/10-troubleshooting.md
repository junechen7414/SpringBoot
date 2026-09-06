## 常見問題與最佳實踐

### 為何使用 RestClient 而非 WebClient？

- **同步設計**: 搭配 Virtual Threads，阻塞變得廉價
- **可讀性**: 避免 WebClient 的非同步傳染性與複雜語法
- **維護性**: 團隊成員更容易理解與除錯

### 為何使用 Resilience4j 取代自定義 Semaphore？

- **標準化**: Spring 生態系推薦方案，社群支援完善
- **監控整合**: 自動整合 Micrometer，無需手動開發
- **擴展性**: 可輕鬆堆疊熔斷、重試等功能

### 為何 entity 用組合（`@Embedded`）而非繼承 `BaseEntity`？

- **背景**: 早期用 `BaseEntity`（`@MappedSuperclass`）集中稽核/軟刪除/樂觀鎖欄位，後來全面改成組合並移除 `BaseEntity`（見 git：`migrate all entities from inheritance to composition`、`remove deprecated unused BaseEntity`）。
- **為何改組合**:
  - 避免深繼承樹，語義更清楚；每個 entity 可自由挑選要嵌入哪些元數據。
  - 不被「基底類別放什麼」綁死——`@Version` 就是放不進去的例子（見下）。
- **⚠️ `@Version` 不能放進 `@Embeddable`（血淚教訓）**: JPA 規格**不支援** `@Version` 出現在 `@Embeddable` 內。曾嘗試把版本欄位收進 `VersionMetadata` embeddable，反覆失敗，最後把 `@Version` 移回每個 entity 才正常運作（見 git：`refactor(util): remove VersionMetadata Embeddable`）。
- **如何套用**:
  - 稽核欄位 → `@Embedded AuditMetadata`；軟刪除欄位 → `@Embedded SoftDeleteMetadata`（兩者皆 `@Embeddable`）。
  - `@Version` **直接宣告在 entity**（`NUMBER(10) DEFAULT 0 NOT NULL`），不要放進任何 `@Embeddable`。
  - 用 `@Builder`（無父類別，不需 `@SuperBuilder`）。
  - 不要再新增 `@MappedSuperclass` 基底類別。

### 如何處理樂觀鎖衝突？

本專案的 `GlobalExceptionHandler` 已內建。衝突屬**預期**行為（有人先改了），對應 409 —— 記錄等級由最終 status 決定，所以自動只記一行 WARN、不印 stack trace：

```java
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public @Nullable ResponseEntity<Object> handleOptimisticLockingFailure(
        ObjectOptimisticLockingFailureException ex, WebRequest request) {
    // status / code / title / type 全由 ErrorCode 決定，不在此硬編碼
    return respond(ex, ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "資料已被其他使用者修改，請重新整理後再試。", request);
}
```

（簽章跟著父類別 `ResponseEntityExceptionHandler` 走：收 `WebRequest`、回 `ResponseEntity<Object>`，
才能與框架內建的那批 handler 共用 `handleExceptionInternal` 這個收尾點。）

呼叫端收到的是 RFC 9457 `application/problem+json`：

```json
{
  "type": "urn:problem:optimistic-lock-conflict",
  "title": "資料版本衝突",
  "status": 409,
  "detail": "資料已被其他使用者修改，請重新整理後再試。",
  "instance": "/order/1",
  "code": "OPTIMISTIC_LOCK_CONFLICT"
}
```

呼叫端請以 `code` 分流（不要剖 `detail` —— 那是給人看的、隨時會改字），並在 UI 上引導重新載入後再送出。

### 容器顯示 `healthy`，但宿主機 `localhost:8787` 拒絕連線？

Windows + WSL 環境下最容易誤判的一種。先確認 podman machine 是不是 rootful：

```bash
podman machine inspect podman-machine-default --format "Rootful={{.Rootful}}"
podman machine ssh "ss -ltn | grep 8787"     # rootful 時這裡「看不到」listener
```

`ss` 看不到 listener 就**不是應用的問題**，不必去查 Spring Boot、`.env` 或防火牆：

| | rootful | rootless |
|---|---|---|
| published port 的實作 | netavark 寫的 nftables DNAT 規則 | rootlessport 建立的真實 `0.0.0.0` listener |
| WSL 內 `ss -ltn` | 看不到 | 看得到 |
| Windows 端 `localhost:<port>` | **每一個 port 都拒絕連線** | 正常 |
| machine 內 `curl localhost:<port>` | 200（DNAT 對本機 output 也生效，所以很容易誤判成「服務是好的」） | 200 |

原因：WSL2 的 localhost 轉發是靠偵測 WSL 內的 listening socket，才去 Windows 端建立對應的 relay；DNAT 規則不是 socket，relay 永遠不會建立。想確認 app 其實活著，可以繞道打 WSL 的 IP（`podman machine ssh "ip -4 addr show eth0"`，該 IP 每次重啟會變）。

**修法：切回 rootless。** 兩邊 storage 不共用，image 與 volume 都要重建：

```powershell
podman compose down -v
podman machine stop
podman machine set --rootful=false
podman machine start
podman compose up -d --build
```

Oracle image 有 10GB 且 `container-registry.oracle.com` 很慢，別讓它重抓 —— 在 machine 內部搬進 rootless storage 快得多：

```bash
podman machine ssh "podman save -o /var/tmp/oracle.tar container-registry.oracle.com/database/free:latest && chmod 644 /var/tmp/oracle.tar && su - user -c 'podman load -i /var/tmp/oracle.tar' && rm -f /var/tmp/oracle.tar"
```

rootless 跑 Oracle 不會有權限問題：`init-scripts` 是從 `/mnt/c` 的 9p drvfs 掛進去、權限一律 `777`，容器內 uid 映射到 subuid 之後照樣讀得到。

> **另一個選項**：`~/.wslconfig` 設 `[wsl2] networkingMode=mirrored`。它不必重建容器，且是**唯一**能讓 LAN 上其他機器直連的做法（rootless 只解決宿主機 localhost，因為 relay 只綁 `127.0.0.1`）。但它是 Windows 全域設定、無法版控、會影響該機器上所有 WSL distro，因此不列為預設解法 —— 只在真的需要 LAN 直連時才加。

> ⚠️ **`podman machine ssh` 的副作用**：從 repo 根目錄執行它，會在該目錄留下一個名為 `NUL` 的檔案（內容是 machine 的 SSH host key）。podman 傳的是 `-o UserKnownHostsFile=NUL`，本意指向 Windows 的 null 裝置，但它呼叫的 msys ssh 把 `NUL` 當成普通相對檔名。`NUL` 是保留裝置名，git 與一般 Windows 工具都刪不掉（`warning: failed to remove NUL: Permission denied`），只能從 WSL 刪：
>
> ```bash
> podman machine ssh "rm -f '/mnt/c/<repo 路徑>/NUL'"
> ```
>
> 刪掉就好，不必加進 `.gitignore` —— 忽略它只會讓下次出現時無聲無息。

### 參考資源

- **專案筆記**: `筆記.md` - 詳細的技術筆記與實作細節
- **文檔目錄**: `docs/` - 各類指南與計劃文件
- **全域指令**: `.github/instructions/Global.instructions.md` - 開發規範與偏好設定
- **Docker Compose**: `docker-compose.yml` - 完整的本地環境配置
- **CI/CD**: `.github/workflows/image-publish.yml` - 自動化流程定義
