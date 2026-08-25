package com.ibm.demo.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 系統或整合層面真的壞了時拋出的例外，由 {@code GlobalExceptionHandler} 記成 ERROR 並帶完整
 * stack trace，對外一律回不透明的 500。
 *
 * <p>與 {@link BusinessException} 的分界是**誰的錯、要不要有人去看**，這也直接決定記錄方式：
 * <ul>
 *   <li>{@link BusinessException} —— 呼叫端的錯（4xx），業務上完全預期 → WARN 一行、無 stack trace。</li>
 *   <li>{@code SystemException} —— 我們或下游的錯 → ERROR + stack trace。這裡的 throw 點有排查
 *       價值，因此**不**沿用 BusinessException 那套關閉 stack trace 的作法。</li>
 * </ul>
 *
 * <p><b>那為什麼不直接讓它冒到 catch-all 的 handleUnexpected？</b>因為 catch-all 只知道「有東西炸了」，
 * 而 throw 這個例外的地方知道「哪個下游、回了什麼狀態」。那些細節過去被字串串接進 message，
 * 但 message 為了不洩漏內部實作既不會回給呼叫端、也沒被記錄 —— 等於白寫。{@link #with} 就是它們的去處。
 *
 * <p><b>刻意沒有 {@link ErrorCode}</b>：ErrorCode 全部常數皆為 4xx（見 {@link BusinessException} 的
 * 前提說明），而這裡的回應一律是不透明的 500 —— 呼叫端不該、也無法對成因分流，給它一個代碼只是
 * 假裝這個錯誤可以被程式處理。
 */
public class SystemException extends RuntimeException {

    /**
     * 排查用的鍵值，**只進 log、不進回應**。
     *
     * <p>下游 URL、遠端狀態碼這類內部細節洩漏給呼叫端沒有好處（它反正只會收到 500），
     * 但值班的人沒有它就得靠猜。
     */
    private final Map<String, Object> context = new LinkedHashMap<>();

    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 附上一組排查用鍵值，回傳 {@code this} 以便在 throw 運算式裡串接：
     * {@snippet : throw new SystemException("下游呼叫失敗", e).with("status", 502); }
     *
     * <p>可變（mutable）是刻意的：這個物件的生命週期就是「建好、串上 context、立刻拋出」，
     * 為了不可變而每次複製一份例外只是徒增噪音。
     */
    public SystemException with(String key, Object value) {
        context.put(Objects.requireNonNull(key, "context key must not be null"), value);
        return this;
    }

    /** 供 handler 記錄用；{@code LinkedHashMap} 的 toString 已是 {@code {k=v, k2=v2}}，無須另外格式化。 */
    public Map<String, Object> getContext() {
        return Collections.unmodifiableMap(context);
    }
}
