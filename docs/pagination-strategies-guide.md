# 分頁策略指南 (Pagination Strategies Guide)

## 本專案目前使用的方式

本專案使用 **Offset-based Pagination**，透過 Spring Data JPA 的 `Pageable` / `Page<T>` 機制實作。

### 實作方式

- Controller 層接收 `Pageable` 參數（`page=頁碼`, `size=每頁筆數`, `sort=排序`）
- Repository 層回傳 `Page<T>`
- 透過 `PageResponse` record 統一封裝回應（`content`, `page`, `size`, `totalElements`, `totalPages`）

底層 SQL 等同於：

```sql
SELECT ... LIMIT {size} OFFSET {page * size}   -- 取資料
SELECT COUNT(*) FROM ...                        -- 算總數（自動產生）
```

---

## 三種主流分頁方式

### 1. Offset-based Pagination（偏移量分頁）

**原理：** 用 `OFFSET + LIMIT` 跳過前 N 筆資料。

**適用場景：**
- 後台管理系統（需要跳頁功能）
- 需要顯示「第 X 頁 / 共 Y 頁」
- 資料量中小（< 百萬筆）
- 表格型 UI（Ant Design Table、Element UI Table）

**不適用場景：**
- 資料量極大時效能差（OFFSET 越大越慢）
- 即時資料流（新增/刪除會導致重複或遺漏）

---

### 2. Cursor-based Pagination（游標分頁 / Keyset Pagination）

**原理：** 用上一頁最後一筆的某個欄位值作為「游標」，查詢 `WHERE id > :lastId LIMIT :size`。

**適用場景：**
- 無限滾動（Infinite Scroll）
- 即時動態資料（社群動態、聊天記錄、通知列表）
- 大資料量（百萬筆以上）
- API 給行動端或第三方串接（GitHub API、Twitter API 都用此方式）
- 「載入更多」按鈕

**不適用場景：**
- 無法跳頁（只能上一頁/下一頁）
- 不容易顯示總頁數
- 排序欄位必須唯一且有索引

---

### 3. Time-based / Seek Pagination（時間游標分頁）

**原理：** Cursor 的變體，用時間戳記作為游標。

**適用場景：**
- 時間序列資料（日誌、事件流、監控數據）
- 按時間排序的 feed

**不適用場景：**
- 同一時間戳有多筆資料時需要額外處理（需搭配 id 做複合游標）

---

## 效能比較

### 查詢效能

| | Offset-based | Cursor-based |
|---|---|---|
| 第 1 頁 | O(size) | O(size) |
| 第 1000 頁 | O(offset + size)，DB 需掃描前面所有筆 | O(size)，永遠只掃描需要的筆數 |
| COUNT 查詢 | 需要額外 `SELECT COUNT(*)` | 不需要 |

### COUNT(*) 時間複雜度

| 資料庫 | COUNT(*) 行為 | 時間複雜度 |
|--------|--------------|-----------|
| **MySQL (InnoDB)** | 需要全表/全索引掃描（MVCC 機制） | **O(N)** |
| **MySQL (MyISAM)** | 有預存行數計數器（無 WHERE 時） | **O(1)** |
| **PostgreSQL** | MVCC 導致需要掃描 | **O(N)** |
| **Oracle** | 走索引快速全掃描 | **O(N)**，常數因子較小 |

### COUNT(*) 實際耗時估算（InnoDB）

| 資料量 | 耗時 |
|--------|------|
| 10,000 筆 | ~1-5ms |
| 100,000 筆 | ~10-50ms |
| 1,000,000 筆 | ~100-500ms |
| 10,000,000 筆 | ~1-5s ← 開始有感 |

> **注意：** Spring Data 的 `Page<T>` 每次查詢都會自動執行一次 `SELECT COUNT(*)`，所以每次分頁請求實際上是兩條 SQL。如果不需要總數，可以用 `Slice<T>` 取代 `Page<T>`。

---

## 綜合比較表

| 面向 | Offset-based | Cursor-based |
|------|-------------|--------------|
| 省掉 COUNT(*) | ❌ 必須執行 | ✅ 完全不需要 |
| 深頁效能 | ❌ O(offset + size) | ✅ O(size)，恆定 |
| 資料一致性 | ❌ 中間有新增/刪除會跳筆或重複 | ✅ 不會 |
| 實作複雜度 | ✅ Spring Data 原生支援 | ⚠️ 需要自行實作 |
| 可快取性 | ❌ 同一頁內容會變 | ✅ cursor 是確定性的 |
| 跳頁支援 | ✅ 支援 | ❌ 不支援 |
| 顯示總頁數 | ✅ 支援 | ❌ 不支援 |

---

## 決策樹

```
1. UI 需要「跳到第 N 頁」或顯示「共 X 頁」嗎？
   ├── YES → Offset-based
   └── NO → 繼續往下

2. 資料的主要排序維度是什麼？
   ├── 時間序列（日誌、事件流、通知、按時間排序的 feed）
   │   └── Time-based Pagination（用 timestamp 作為 cursor）
   │       ⚠️ 若同一時間戳可能有多筆 → 需搭配 id 做複合游標
   │
   └── 非時間序列（ID、名稱、自訂排序）
       └── 繼續往下

3. 資料量是否超過百萬筆，或深頁有效能問題？
   ├── YES → Cursor-based（用 ID 或排序欄位作為 cursor）
   └── NO → 兩者皆可，但 Cursor-based 仍更優

4. 資料是否頻繁新增/刪除，使用者瀏覽時不能有重複或遺漏？
   ├── YES → Cursor-based
   └── NO → 兩者皆可

5. 前端是「無限滾動」還是「分頁按鈕」？
   ├── 無限滾動 → Cursor-based
   └── 分頁按鈕（含頁碼） → Offset-based
```

### 什麼時候選 Time-based 而不是一般的 Cursor-based？

| 條件 | 選擇 |
|------|------|
| 資料天然按時間排序，且使用者關心的是「某個時間點之後的資料」 | Time-based |
| 需要支援「查看某個時間區間的資料」 | Time-based |
| 排序欄位是 ID 或其他業務欄位 | 一般 Cursor-based |
| 同一秒可能有大量資料（高併發寫入） | Time-based + ID 複合游標 |

### 核心原則

> **如果 UI/API 不需要顯示「總頁數」和「當前第幾頁」，Cursor-based 幾乎在所有面向都優於 Offset-based。**

### 實務建議

| 場景 | 建議方式 |
|------|---------|
| 面向終端使用者的 API | Cursor-based（預設選擇） |
| 面向後台管理員的 API | Offset-based（需要跳頁 + 總數） |

---

## Cursor-based 實作範例（Spring Boot）

### Response DTO

```java
@Schema(description = "游標分頁回應")
public record CursorPageResponse<T>(
        @Schema(description = "資料內容") List<T> content,
        @Schema(description = "每頁筆數", example = "20") int size,
        @Schema(description = "是否有下一頁") boolean hasNext,
        @Schema(description = "下一頁游標（傳回給下次請求）", example = "42") String nextCursor
) {}
```

### Repository

```java
public interface ProductRepository extends JpaRepository<Product, Integer> {

    // 第一頁（沒有 cursor）
    @Query("SELECT p FROM Product p ORDER BY p.id ASC")
    List<Product> findFirstPage(Pageable pageable);

    // 後續頁（有 cursor）
    @Query("SELECT p FROM Product p WHERE p.id > :cursor ORDER BY p.id ASC")
    List<Product> findAfterCursor(@Param("cursor") Integer cursor, Pageable pageable);
}
```

### Service

```java
public CursorPageResponse<GetProductListResponse> getProductListByCursor(Integer cursor, int size) {
    // 多查一筆來判斷有沒有下一頁
    Pageable pageable = PageRequest.of(0, size + 1);

    List<Product> products;
    if (cursor == null) {
        products = productRepository.findFirstPage(pageable);
    } else {
        products = productRepository.findAfterCursor(cursor, pageable);
    }

    boolean hasNext = products.size() > size;
    if (hasNext) {
        products = products.subList(0, size);
    }

    List<GetProductListResponse> content = products.stream()
            .map(this::mapProductToListResponse)
            .toList();

    String nextCursor = hasNext && !products.isEmpty()
            ? String.valueOf(products.get(products.size() - 1).getId())
            : null;

    return new CursorPageResponse<>(content, size, hasNext, nextCursor);
}
```

### Controller

```java
@GetMapping("/products")
@Operation(summary = "獲取商品列表（游標分頁）")
public ResponseEntity<CursorPageResponse<GetProductListResponse>> getProductList(
        @Parameter(description = "游標（上一頁最後一筆的 ID），第一頁不傳")
        @RequestParam(required = false) Integer cursor,
        @Parameter(description = "每頁筆數", example = "20")
        @RequestParam(defaultValue = "20") int size) {

    CursorPageResponse<GetProductListResponse> response =
            productService.getProductListByCursor(cursor, size);
    return ResponseEntity.ok(response);
}
```

### API 使用流程

```
# 第一頁
GET /products?size=20
→ { "content": [...], "size": 20, "hasNext": true, "nextCursor": "42" }

# 第二頁
GET /products?size=20&cursor=42
→ { "content": [...], "size": 20, "hasNext": true, "nextCursor": "67" }

# 最後一頁
GET /products?size=20&cursor=95
→ { "content": [...], "size": 20, "hasNext": false, "nextCursor": null }
```

### 進階：複合排序的 Cursor

如果需要按 `createdAt DESC, id DESC` 排序，cursor 需要是複合的：

```java
@Query("SELECT p FROM Product p WHERE (p.createdAt < :cursorTime) " +
       "OR (p.createdAt = :cursorTime AND p.id < :cursorId) " +
       "ORDER BY p.createdAt DESC, p.id DESC")
List<Product> findAfterCursor(@Param("cursorTime") LocalDateTime cursorTime,
                              @Param("cursorId") Integer cursorId,
                              Pageable pageable);
```

此時通常會把 cursor 做 Base64 編碼，讓前端當作不透明 token 傳遞。

---

**最後更新**: 2025-06-10
**維護者**: Bobby
