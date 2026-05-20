package com.ibm.demo.util;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 版本元數據 - 可嵌入的樂觀鎖欄位
 * 用於實現樂觀鎖機制
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "版本元數據")
public class VersionMetadata {
    
    @Version
    @Column(name = "VERSION", columnDefinition = "NUMBER(10) DEFAULT 0", nullable = false)
    @Schema(description = "樂觀鎖版本", example = "0")
    private Integer version = 0;
}
