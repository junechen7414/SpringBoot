package com.ibm.demo.util;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 基礎實體類別 - 已棄用
 * 
 * @deprecated 此類別已被棄用，請使用組合模式替代繼承：
 * <ul>
 * <li>{@link AuditMetadata} - 用於審計欄位（createdAt, updatedAt）</li>
 * <li>{@link SoftDeleteMetadata} - 用於軟刪除欄位（deleted, deletedAt）</li>
 * <li>{@link VersionMetadata} - 用於樂觀鎖版本控制（version）</li>
 * </ul>
 * 
 * <p>使用範例：</p>
 * <pre>{@code
 * @Entity
 * public class MyEntity {
 *     @Id
 *     private Long id;
 *     
 *     // 組合所需的功能
 *     @Embedded
 *     @Builder.Default
 *     private AuditMetadata auditMetadata = new AuditMetadata();
 *     
 *     @Embedded
 *     @Builder.Default
 *     private SoftDeleteMetadata softDeleteMetadata = new SoftDeleteMetadata();
 *     
 *     @Embedded
 *     @Builder.Default
 *     private VersionMetadata versionMetadata = new VersionMetadata();
 *     
 *     // 其他業務欄位...
 * }
 * }</pre>
 * 
 * @since 1.0
 */
@Deprecated(since = "2.0", forRemoval = true)
@Getter
@Setter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Schema(description = "基礎實體欄位 (已棄用，請使用組合模式)")
public abstract class BaseEntity {
    
    /**
     * @deprecated 使用 {@link AuditMetadata#getCreatedAt()} 替代
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    @Schema(description = "創建時間", example = "2024-06-01T12:00:00")
    private LocalDateTime createdAt;

    /**
     * @deprecated 使用 {@link AuditMetadata#getUpdatedAt()} 替代
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    @Schema(description = "更新時間", example = "2024-06-01T12:00:00")
    private LocalDateTime updatedAt;

    /**
     * @deprecated 使用 {@link SoftDeleteMetadata#getDeleted()} 替代
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Column(name = "DELETED", columnDefinition = "NUMBER(1)")
    @Schema(description = "是否刪除", example = "false")
    @Builder.Default
    private Boolean deleted = false;

    /**
     * @deprecated 使用 {@link SoftDeleteMetadata#getDeletedAt()} 替代
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Column(name = "DELETED_AT", columnDefinition = "TIMESTAMP")
    @Schema(description = "刪除時間", example = "2024-06-01T12:00:00")
    private LocalDateTime deletedAt;

    /**
     * @deprecated 使用 {@link VersionMetadata#getVersion()} 替代
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Version
    @Column(name = "VERSION", columnDefinition = "NUMBER(10) DEFAULT 0", nullable = false)
    @Schema(description = "樂觀鎖版本", example = "0")
    private Integer version;
}