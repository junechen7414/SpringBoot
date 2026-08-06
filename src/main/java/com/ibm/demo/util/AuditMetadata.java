package com.ibm.demo.util;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 審計元數據 - 可嵌入的審計欄位
 * 包含創建時間和更新時間
 * <p>
 * <b>{@code @EntityListeners} 必須標在使用本類別的 {@code @Entity} 上，不能標在這裡。</b>
 * JPA 規範只在 {@code @Entity} / {@code @MappedSuperclass} 上處理
 * {@link org.springframework.data.jpa.domain.support.AuditingEntityListener}，標在
 * {@code @Embeddable} 會被<b>靜默忽略</b> —— 沒有錯誤、沒有警告，只是 CREATED_AT /
 * UPDATED_AT 每次 insert 都寫 null（而 V1 migration 沒有 NOT NULL，DB 也不會擋）。
 * 這是從 {@code @MappedSuperclass} 繼承改為組合時容易踩到的差異：前者的 listener 會被
 * 子類繼承，後者不會。
 * <p>
 * 欄位本身放在 {@code @Embeddable} 裡是可行的 —— Spring Data 的
 * {@code MappingAuditableBeanWrapperFactory} 會沿 mapping context 走訪嵌套路徑找到
 * {@code @CreatedDate} / {@code @LastModifiedDate}；但前提是各 entity 的 {@code @Embedded}
 * 欄位已初始化（不可為 null，否則寫入中途會拋 MappingException）。
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "審計元數據")
public class AuditMetadata {
    
    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    @Schema(description = "創建時間", example = "2024-06-01T12:00:00")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    @Schema(description = "更新時間", example = "2024-06-01T12:00:00")
    private LocalDateTime updatedAt;
}
