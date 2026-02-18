package com.ibm.demo.util;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    @Schema(description = "創建時間", example = "2024-06-01T12:00:00")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    @Schema(description = "更新時間", example = "2024-06-01T12:00:00")
    private LocalDateTime updatedAt;

    @Column(name = "DELETED", columnDefinition = "NUMBER(1)")
    @Schema(description = "是否刪除", example = "false")
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "DELETED_AT", columnDefinition = "TIMESTAMP")
    @Schema(description = "刪除時間", example = "2024-06-01T12:00:00")
    private LocalDateTime deletedAt;
}
