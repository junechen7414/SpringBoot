# 🔐 認證外包給外部 IdP 的遷移路徑

> **最後更新**: 2026-08-05
> **專案**: Spring Boot Demo Application
> **現況**: in-memory HTTP Basic（佔位方案，見 `config/SecurityConfig.java`）
> **目標**: 本應用只當 OAuth2 Resource Server，authN/authZ 交給外部 IdP

---

## 📋 目錄

1. [為什麼要外包](#-為什麼要外包)
2. [目標形狀](#-目標形狀)
3. [遷移步驟](#-遷移步驟)
4. [已知坑](#-已知坑)
5. [成本與時機](#-成本與時機)
6. [為什麼現在不做](#-為什麼現在不做)

---

## 🎯 為什麼要外包

**責任邊界**：使用者存放、密碼雜湊與輪替、帳號鎖定、MFA、token 簽發與撤銷 —— 這些都是一個獨立的問題領域，有成熟的專門工具（Keycloak、Auth0、Entra ID、Okta）。業務應用自己實作，等於用一份沒人專門維護的程式碼去對抗一整類攻擊面。**全部自己做，結果通常不是全能而是全不能。**

**微服務尤其如此**：本專案的三個 domain（account / product / order）將來若真的拆成獨立 process，「每個服務各養一份使用者表 + 各自實作密碼比對」是明確的反模式 —— 同一個使用者要在 N 個地方建立、N 個地方輪替密碼，而且任一個服務的雜湊實作出錯就是整體破口。正確做法是**單一 IdP 簽發 token，每個服務只驗簽章與 claims**（無狀態、可水平擴展、不需要彼此同步）。

**Spring 的慣用形狀就是這樣**：`spring-boot-starter-oauth2-resource-server` 加一行 `issuer-uri`，Spring 會自動抓 IdP 的 JWKS 並驗簽。應用端的 `UserDetailsService`、`PasswordEncoder`、使用者存放全部消失 —— 這才是「透過依賴引入，而不是自己實作」的意思。

**另一條常見路徑（service-to-service）**：服務間認證也可以整層下沉到基礎設施 —— API gateway 統一驗 token 後轉發，或 service mesh 以 mTLS 處理雙向身分（Istio / Linkerd）。應用程式碼因此完全不必知道認證存在。適合服務數量多、且已經有平台團隊維護 mesh 的情境。

---

## 🏗️ 目標形狀

```
                    ┌──────────────┐
   使用者 ──登入──► │  IdP         │ ──簽發 access token（JWT）
                    │  (Keycloak)  │
                    └──────┬───────┘
                           │ JWKS（公鑰）
                           ▼
   使用者 ──Bearer token──► ┌─────────────────────┐
                            │  本應用             │ 只做：驗簽章、驗 iss/aud/exp、
   *Client ──Bearer token──►│  Resource Server    │ 把 role claim 映射成 authority
   （client_credentials）   └─────────────────────┘
```

`SecurityConfig` 遷移後應該只剩兩件事：**放行清單 + `.oauth2ResourceServer(...)`**。`UserDetailsService`、`PasswordEncoder`、`AppProperties.Auth` 全部刪除。

---

## 🚀 遷移步驟

> 每步標出要動的檔案。建議走 branch + PR（跨 domain + 影響下游 E2E，屬高風險變更，見 `high-risk-pr-workflow` skill）。

### 1. 起一個 IdP（`docker-compose.yml`）

加一個 Keycloak service，用 realm import 讓設定進 git（不要用手點 UI 的方式建 realm，那無法版控也無法重現）：

```yaml
  keycloak:
    image: quay.io/keycloak/keycloak:latest
    container_name: keycloak
    command: ["start-dev", "--import-realm"]
    environment:
      - KC_BOOTSTRAP_ADMIN_USERNAME=${KEYCLOAK_ADMIN_USERNAME}
      - KC_BOOTSTRAP_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD}
    ports:
      - "8080:8080"
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
    networks:
      - backend-net
```

realm 內至少要有兩個 client：

| client | 用途 | 設定要點 |
|---|---|---|
| `demo-api` | 對外 API 的 audience | public 或 confidential 視呼叫端而定 |
| `demo-internal` | 內部 `*Client` 自呼叫 | **service account 啟用**、`client_credentials` grant、role `INTERNAL` |

`app` service 的 `depends_on` 要加上 `keycloak`。

### 2. 依賴（`build.gradle`）

```gradle
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'  // 驗 token
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'           // 內部呼叫取 token
```

`spring-boot-starter-security` 保留（前者依賴它）。

> ⚠️ Boot 4 對 starter 做了模組化拆分（本專案已因此把 RestClient 自動配置改成 `spring-boot-starter-restclient`）。實作時**先確認這兩個 OAuth2 starter 在 Boot 4.1 的實際 artifact 座標**，不要照抄 Boot 3 的文章。

### 3. 設定（`application.yml` / `application-dev.yml`）

```yaml
spring:
  security:
    oauth2:
      # 驗 inbound token：Spring 會從 issuer 的 /.well-known 抓 JWKS 並自動快取、輪替
      resourceserver:
        jwt:
          issuer-uri: ${IDP_ISSUER_URI:http://localhost:8080/realms/demo}
      # 取 outbound token：供 *Client 自呼叫用
      client:
        registration:
          internal:
            client-id: demo-internal
            client-secret: ${IDP_INTERNAL_CLIENT_SECRET}
            authorization-grant-type: client_credentials
        provider:
          keycloak:
            token-uri: ${IDP_ISSUER_URI:http://localhost:8080/realms/demo}/protocol/openid-connect/token
```

同時**移除** `app.auth.*` 整段，以及 `AppProperties.Auth` 這個 nested class。

### 4. `SecurityConfig` 大幅縮小

```java
@Bean
@Profile("!openapi")
SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health/**").permitAll()
            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
}
```

刪除 `userDetailsService()`。放行清單、STATELESS、csrf 的理由完全不變（那些判斷與認證機制無關）。`@Profile("openapi")` 那條全 `permitAll` 的 chain **不動**。

### 5. 內部自呼叫改帶 bearer token（`config/RestClientConfig.java`）

目前第 84 行是：

```java
.defaultHeaders(headers -> headers.setBasicAuth(auth.getInternalUsername(), auth.getInternalPassword()))
```

改成掛 OAuth2 client 的 request interceptor（Spring Security 提供 `OAuth2ClientHttpRequestInterceptor`，指定 `ClientRegistrationId` 為 `internal`），或自行注入 `OAuth2AuthorizedClientManager` 取 token 後塞 `Authorization: Bearer`。

> ⚠️ 本專案為 Boot 4.1 / Spring Security 7，**實作時先確認該 interceptor 類別在此版本的實際套件路徑與 API**。

**這步是效能上的淨勝利**：`OAuth2AuthorizedClient` 會把 token 快取到過期前，因此 N 次自呼叫共用一顆 token，不再像 Basic 那樣每個請求重跑一次密碼驗證（現況刻意改用 `{noop}` 就是為了避免這個成本，見 `SecurityConfig` 的註解）。

### 6. 這時才有真正的 authZ

把 IdP 的 role claim 映射成 Spring authority：

```java
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("realm_access.roles");  // Keycloak 的 realm role 位置
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
}
```

然後加 `@EnableMethodSecurity`，就能用 `@PreAuthorize("hasRole('INTERNAL')")`。現況保留的 `roles("API")` / `roles("INTERNAL")` 就是為了讓這步是「對上名字」而不是「重新設計」。

**第一批候選規則** —— 從 `*Client` 介面反推，以下是純編排端點，只有內部自呼叫會用到，適合限 `INTERNAL`：

| 端點 | 呼叫者 |
|---|---|
| `POST /product/reserve`、`/release`、`/adjust-stock` | `ProductClient` |
| `GET /product/batch` | `ProductClient` |
| `GET /account/{id}/order-eligibility` | `AccountClient` |
| `GET /order/account/{accountId}/existence` | `OrderClient` |

> ✅ **已查核下游，上表可安全啟用**（2026-08-05 對 `junechen7414/Playwright-TS`）。下游唯一的呼叫面是
> `services/apis/springboot-api-client.ts`，只用 `/account`、`/product`、`/order`（含 `/{id}`、
> `/order/account/{accountId}`）的 CRUD 與列表；`reserve`、`release`、`adjust-stock`、`batch`、
> `order-eligibility`、`exists` 這些字串在該 repo **只出現在產出的 `docs/swagger.json`**，沒有任何測試引用。
> 這仍是行為變更而非重構 —— 下次調整這批端點前要重新查核，下游隨時可能新增測試。

> 🚨 **例外：`POST /PlaywrightTestData/createOrderPrecondition` 必須維持對外部呼叫端開放。**
> 下游用它準備 order 測試的前置資料（`SpringbootApiClient.prepareOrdersTestData()`），而
> `testdata/PlaywrightTestDataController` 沒有 `@Profile` 限制、在所有 profile 都存在。若順手把它一起限成
> `INTERNAL`，整組 order E2E 會在前置階段就失敗，而且失敗點看起來像是測試資料問題、不像授權問題。

### 7. 測試

- **web / unit 層**：用 `spring-security-test` 的 `SecurityMockMvcRequestPostProcessors.jwt()` 直接偽造帶 authority 的 token，不需要真 IdP。該依賴**已經在 `build.gradle` 裡**。
- **整合測試**：若要真 issuer，加 Keycloak testcontainer（社群模組 `dasniko/testcontainers-keycloak`）。注意本專案的 `BaseIntegrationTest` 刻意手動 `start()` 容器以避開 singleton 被提早關閉的坑 —— 新容器請沿用同一模式。
- **`openapi` profile 不受影響**（全 `permitAll`），`./gradlew generateOpenApiDocs` 不需要 IdP。

### 8. 下游與環境衝擊

- **Playwright E2E repo（`junechen7414/Playwright-TS`）**：要改成先向 IdP 取 token、再帶 `Authorization: Bearer`。這是跨 repo 的破壞性變更，兩邊要協調上線順序。點名要改的檔案（2026-08-05 查核）：

  | 檔案 | 現況 | 要改成 |
  |---|---|---|
  | `playwright.config.ts` | 自行組 `Basic ${base64(API_USERNAME:API_PASSWORD)}`，透過 `springboot-api` project 的 `extraHTTPHeaders` 送出 | `Authorization: Bearer ${process.env.API_TOKEN}`。`extraHTTPHeaders` 是 config 載入時求值、**不能 `await`**，所以 token 要在 `globalSetup` 取好塞進 env —— 該 repo 已有一份被註解停用的 `global-setup.ts`，正好在此重新啟用 |
  | `docker-compose.test.yml` | 把 `API_USERNAME` / `API_PASSWORD` 注入 app 容器覆寫 `app.auth.api-*` | 加 keycloak service（`depends_on: condition: service_healthy`），env 換成 `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` |
  | `.github/workflows/springboot.yml` | `API_USERNAME` / `API_PASSWORD` 出現**兩處**（Start Environment 給容器、Run Playwright 給測試） | 換成 client id / secret，兩處都要改；GitHub Secrets 同步新增 |
  | `.env.example` | 說明「後端在 PR #66 後加上 HTTP Basic 認證」、列 `API_USERNAME` / `API_PASSWORD` | 改為 IdP client 憑證與 issuer |

  token 存活期要涵蓋整個 job（該 workflow `timeout-minutes: 60`），否則長測試跑到後半段會集體 401；不想放長效 token 就得改成每個 fixture 自己取。

  **不受影響的部分**：`tests/api/Network.spec.ts` 雖然測 401 情境，但它是 `page.route` 全 mock 打 `https://demo-api.local/endpoint`，且不在 `--project=springboot-api` 的 `testMatch` 內，與真實後端無關。`pnpm run api-spec:update` 讀的是 repo 內已 commit 的 `docs/swagger.json`（不是打線上 server），只要 API 形狀沒變就不必重跑。
- **`.env.example` / `.env`**：`API_USERNAME` / `API_PASSWORD` / `INTERNAL_USERNAME` / `INTERNAL_PASSWORD` 換成 `IDP_ISSUER_URI`、`IDP_INTERNAL_CLIENT_SECRET`、`KEYCLOAK_ADMIN_*`。
- **`docs/agents/06-architecture.md`、`docs/handout/06-config-security-ops.md`、`CLAUDE.md`** 要同步（見 `docs/agents/05-code-standards.md` 的同步要求）。

---

## ⚠️ 已知坑

**1. issuer 主機名不一致會直接讓驗證失敗**
JWT 的 `iss` claim 必須與 `issuer-uri` 完全相符。容器內是 `http://keycloak:8080/realms/demo`，從主機瀏覽器拿到的 token 卻是 `http://localhost:8080/realms/demo` —— 兩者不相等，驗證就掛。解法與本專案處理 `ORACLE_DB_HOST` 的手法同源：把 host 抽成帶預設值的變數，並在 Keycloak 設定 `KC_HOSTNAME` 讓簽發端與驗證端看到同一個 issuer。

> **下游 E2E 一定會踩到這個坑**：`docker-compose.test.yml` 的 app 跑在 compose 網路內、只能用服務名 `keycloak:8080` 連 IdP，而 Playwright 跑在 runner 主機上、只能用 `localhost:8080` 取 token。token 的 `iss` 是簽發時那個 URL，於是**每個請求都 401，錯誤訊息不會提到主機名**。要先把 Keycloak 的 frontend URL（`KC_HOSTNAME_URL`）釘成單一標準值讓兩邊看到同一個字串，具體配法需實測驗證。這也是遷移該優先在下游驗的一項。

**2. loopback 自呼叫的 audience**
`client_credentials` 拿到的 token 必須被本應用接受（aud / scope 對得上）。若之後加了 audience 驗證，記得把本應用自己也列進去。

**3. client_credentials token 沒有使用者**
`@PreAuthorize` 若寫了依賴使用者身分的條件（例如「只能查自己的 order」），內部自呼叫會沒有 `sub` 可用。編排端點的規則要以 role 為準，不要混入使用者條件。

**4. token 過期與 clock skew**
容器與主機時鐘不同步會讓 token 提前失效。Spring 預設允許 60 秒 skew，`start-dev` 模式的 Keycloak 在筆電休眠後尤其容易踩到。

---

## 💰 成本與時機

| 項目 | 成本 |
|---|---|
| Keycloak container | 記憶體與啟動時間（本機已有 Oracle 容器，資源競爭要留意，見 `integration-test-runner` skill） |
| realm 維護 | realm JSON 進 git、client secret 進 env、升級 Keycloak 時的相容性 |
| 測試 | 多一層 mock token 或多一個 testcontainer |
| 下游 | Playwright E2E repo 必須同步改 |

**建議觸發條件**（滿足任一就值得做）：

- domain 真的拆成獨立 process（此時 IdP 從「額外複雜度」變成「唯一合理解」）。
- 系統開始面向真實使用者（需要註冊、密碼輪替、MFA、帳號鎖定）。
- 需要細緻的 authZ（多角色、租戶隔離、scope）。

---

## 🤔 為什麼現在不做

目前是**單一 deployable**，所謂「微服務」是 `*Client` 透過 loopback HTTP 打回自己。在這個形狀下：

- 加 IdP 會讓**認證機制比它保護的東西更複雜** —— 為了兩個機器帳號養一個完整的 IdP。
- 解耦的真正收益（單一使用者來源、服務間零共享狀態）要等 domain 真的分家才兌現。

所以現況刻意停在「誠實的佔位方案」：in-memory 機器帳號、`{noop}` 逐字比對、沒有假裝有 authZ，並把路徑寫在這份文件裡 —— 真要做的時候是照步驟走，不是從零設計。
