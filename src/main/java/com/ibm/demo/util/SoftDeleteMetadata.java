package com.ibm.demo.util;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 軟刪除元數據 - 可嵌入的軟刪除欄位
 * 包含刪除標記和刪除時間
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "軟刪除元數據")
public class SoftDeleteMetadata {
    
    @Column(name = "DELETED", columnDefinition = "NUMBER(1)")
    @Schema(description = "是否刪除", example = "false")
    private Boolean deleted = false;

    @Column(name = "DELETED_AT", columnDefinition = "TIMESTAMP")
    @Schema(description = "刪除時間", example = "2024-06-01T12:00:00")
    private LocalDateTime deletedAt;
    
    /**
     * 標記為已刪除
     */
    public void markAsDeleted() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
    
    /**
     * 檢查是否已刪除
     */
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
}
