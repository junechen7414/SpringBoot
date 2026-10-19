package com.ibm.demo.exception;

import java.net.URI;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * API 對外可回傳的錯誤代碼<b>唯一登錄處</b>。
 *
 * <p>三個欄位如何對到 RFC 9457 的 {@code application/problem+json}：
 * <ul>
 *   <li>{@link #getCode()} —— 機器可讀的穩定識別碼，直接就是 enum 常數名。呼叫端 switch 的就是它。</li>
 *   <li>{@link #getType()} —— 由 code 機械推導的 problem type URI（{@code urn:problem:<kebab-case>}）。
 *       日後要換成可解析的文件網址，只要改 {@link #typeOf} 一處。</li>
 *   <li>{@link #getTitle()} —— 這「類」問題的人類可讀摘要，同一個 code 永遠是同一句話。
 *       單次請求的細節屬於 {@code detail}，不放這裡。</li>
 * </ul>
 *
 * <p><b>{@link #getFixedDetail()}：基礎設施類錯誤的固定說明。</b>這幾個錯誤的 {@code detail} 不隨請求
 * 變化（沒有「哪個商品、缺多少」可說），而且刻意不帶內部資訊，所以同一句話放在這裡、由
 * {@code GlobalExceptionHandler} 產生本地錯誤、{@code RestClientErrorHandler} 轉譯下游錯誤時共用同一個來源，
 * 兩邊的回應才保證逐字一致，也不必讓翻譯下游錯誤的元件反過來依賴負責組回應的 advice。
 * 業務錯誤沒有固定說明（回 {@code null}），它們的 {@code detail} 由 throw 點決定。
 *
 * <p><b>為什麼 code 就是 {@code name()}：</b>舊版另外帶一組 {@code SYS_001}／{@code PRODUCT_003}
 * 風格的字串，於是同一個錯誤有兩個名字 —— 測試與程式碼用常數名、對外回應用編號，而編號本身早已
 * 出現斷點（有 {@code ACCOUNT_002} 沒有 {@code 001}）證明它不承載任何意義。收成一個命名空間後，
 * 「程式裡看到的」與「線路上看到的」保證一致，不會漂移。
 *
 * <p><b>基礎設施類錯誤（{@code VALIDATION_FAILED} 之後那幾個）也登錄在此</b>，因為它們同樣會出現在
 * {@code code} 欄位；分兩個地方放等於讓「API 可能回哪些 code」沒有單一答案。至於框架自己攔下的協定
 * 層錯誤（405、415…）不在此列 —— 它們的 code 由 {@code GlobalExceptionHandler} 從 HTTP 狀態名推導。
 */
@Getter
public enum ErrorCode {

    // ---- 業務錯誤：由 domain service 拋 BusinessException 帶出，必須是 4xx ----
    ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED(HttpStatus.BAD_REQUEST, "帳戶仍有訂單，無法刪除"),
    PRODUCT_ALREADY_EXIST(HttpStatus.BAD_REQUEST, "商品名稱已存在"),
    PRODUCT_STILL_HAS_RESERVATION_CAN_NOT_BE_DELETED(HttpStatus.BAD_REQUEST, "商品仍有保留庫存，無法刪除"),
    PRODUCT_STOCK_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "商品庫存不足"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到資源"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "無效的請求"),

    // ---- 基礎設施錯誤：由 GlobalExceptionHandler 從框架／函式庫例外翻譯而來 ----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "參數驗證失敗"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "需要認證", "需要有效的認證憑證。"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "權限不足", "沒有權限執行此操作。"),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "資料版本衝突", "資料已被其他使用者修改，請重新整理後再試。"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "請求過於頻繁", "請求過於頻繁，請稍後再試。"),
    BULKHEAD_FULL(HttpStatus.SERVICE_UNAVAILABLE, "系統負載過高", "系統負載過高，請稍後再試。"),
    CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "服務暫時不可用", "服務暫時不可用，請稍後再試。"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "伺服器內部錯誤", "系統發生未預期的錯誤，請稍後再試。");

    /** URN 而非 http(s)：目前沒有錯誤說明文件站，給個假網址反而是誤導。 */
    private static final String TYPE_PREFIX = "urn:problem:";

    private final HttpStatus status;
    private final String title;
    private final URI type;
    private final @Nullable String fixedDetail;

    ErrorCode(HttpStatus status, String title) {
        this(status, title, null);
    }

    ErrorCode(HttpStatus status, String title, @Nullable String fixedDetail) {
        this.status = status;
        this.title = title;
        this.fixedDetail = fixedDetail;
        // name() 在 enum 建構子內可用（name 由 Enum 的建構子先行設定）；typeOf 只讀 TYPE_PREFIX，
        // 而它是編譯期常數會被 inline，因此不受 enum 常數初始化順序影響。
        this.type = typeOf(name());
    }

    /** code 即常數名 —— 讓「程式裡的識別碼」與「線路上的識別碼」不可能分岔。 */
    public String getCode() {
        return name();
    }

    /**
     * 把一個 SCREAMING_SNAKE 的 code 轉成 problem type URI。
     *
     * <p>公開給 {@code GlobalExceptionHandler} 用在框架層錯誤（例如 405 的 code 是
     * {@code METHOD_NOT_ALLOWED}），確保所有錯誤的 {@code type} 都由同一條規則產生。
     */
    public static URI typeOf(String code) {
        return URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
