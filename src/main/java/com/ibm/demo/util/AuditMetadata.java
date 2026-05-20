package com.ibm.demo.util;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EntityListeners;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 審計元數據 - 可嵌入的審計欄位
 * 包含創建時間和更新時間
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
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
