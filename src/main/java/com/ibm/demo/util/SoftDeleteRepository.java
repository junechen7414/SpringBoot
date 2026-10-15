package com.ibm.demo.util;

import java.time.LocalDateTime;

/**
 * 有終止動作（Deactivate／Delist／Cancel）的 entity 共用的軟刪除契約。
 *
 * <p>{@code now} 由呼叫端以 {@code LocalDateTime.now(clock)} 傳入，同時寫進 {@code DELETED_AT} 與
 * {@code UPDATED_AT}：bulk UPDATE 會繞過 entity listener，{@code @LastModifiedDate} 不會觸發，
 * 而 JPQL 的 {@code CURRENT_TIMESTAMP} 是 DB 的時鐘。時間來源見 {@code config.AuditTimeConfig}。
 *
 * @return 更新到的列數；0 代表 version 不符（或資料已不存在），由呼叫端轉成樂觀鎖衝突
 */
public interface SoftDeleteRepository<ID> {
    int softDeleteById(ID id, Integer version, LocalDateTime now);
}
