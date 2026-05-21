package com.ibm.demo.product;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLRestriction;

import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.util.AuditMetadata;
import com.ibm.demo.util.SoftDeleteMetadata;

import io.swagger.v3.oas.annotations.media.Schema; // Import Swagger schema annotation
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@SQLRestriction("DELETED = false AND SALE_STATUS = 1001") // 只選擇未刪除且上架的商品
@Table(name = "PRODUCT")
@Schema(description = "商品資訊")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq_gen") // 使用 Sequence 生成主鍵
    @SequenceGenerator(name = "product_seq_gen", sequenceName = "product_id_seq", allocationSize = 1) // 定義 Sequence
    @Schema(description = "商品編號", example = "1")
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    private Integer id;

    @Column(name = "NAME", columnDefinition = "NVARCHAR2(100)", nullable = false)
    @Schema(description = "商品名稱", example = "商品名稱")
    private String name;

    @Column(name = "PRICE", columnDefinition = "NUMBER(12,4)", nullable = false)
    @Schema(description = "價格", example = "100")
    private BigDecimal price;

    @Column(name = "SALE_STATUS", columnDefinition = "NUMBER(4)", nullable = false)
    @Schema(description = "上架狀態", example = "1001")
    private Integer saleStatus;

    @Column(name = "AVAILABLE", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "可用庫存", example = "10")
    @Builder.Default
    private Integer available = 0;

    @Column(name = "RESERVED", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "保留庫存", example = "5")
    @Builder.Default
    private Integer reserved = 0;

    // 組合：審計欄位
    @Embedded
    @Builder.Default
    private AuditMetadata auditMetadata = new AuditMetadata();

    // 組合：軟刪除欄位
    @Embedded
    @Builder.Default
    private SoftDeleteMetadata softDeleteMetadata = new SoftDeleteMetadata();

    // 樂觀鎖版本（@Version 不能在 @Embeddable 中使用，必須直接定義在實體類別）
    @Version
    @Column(name = "VERSION", columnDefinition = "NUMBER(10) DEFAULT 0", nullable = false)
    @Schema(description = "樂觀鎖版本", example = "0")
    @Builder.Default
    private Integer version = 0;

    public void restore() {
        this.softDeleteMetadata.setDeleted(false);
        this.softDeleteMetadata.setDeletedAt(null);
        this.setSaleStatus(ProductStatus.AVAILABLE.getCode()); // 恢復上架狀態
    }
}
