package com.ibm.demo.product;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.util.BaseEntity;

import io.swagger.v3.oas.annotations.media.Schema; // Import Swagger schema annotation
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
// @Table(name = "PRODUCT", indexes = {
// @Index(name = "pk_PRODUCT", columnList = "ID", unique = true) // 同@Id的效用
// })
@SuperBuilder
@SQLDelete(sql = "UPDATE PRODUCT SET DELETED = 1,DELETED_AT = CURRENT_TIMESTAMP,SALE_STATUS = 1002 WHERE ID = ?") // 軟刪除，將
                                                                                                                  // SALE_STATUS
                                                                                                                  // 設為不可銷售
@SQLRestriction("DELETED = 0 AND SALE_STATUS = 1001") // 只選擇未刪除且可銷售的資料
@Table(name = "PRODUCT")
@Schema(description = "商品資訊")
public class Product extends BaseEntity {
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
    @Schema(description = "銷售狀態", example = "1001")
    private Integer saleStatus;

    @Column(name = "AVAILABLE", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "庫存量", example = "10")
    @Builder.Default
    private Integer available = 0;

    @Column(name = "RESERVED", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "預留庫存量", example = "5")
    @Builder.Default
    private Integer reserved = 0;

    public void restore() {
        this.setDeleted(false);
        this.setSaleStatus(ProductStatus.AVAILABLE.getCode()); // 恢復銷售狀態為可銷售
    }
}