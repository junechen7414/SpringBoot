package com.ibm.demo.product;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema; // Import Swagger schema annotation
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
// @Table(name = "PRODUCT", indexes = {
// @Index(name = "pk_PRODUCT", columnList = "ID", unique = true) // 同@Id的效用
// })
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
    @Schema(description = "銷售狀態", example = "1001")
    private Integer saleStatus;

    @Column(name = "STOCK_QTY", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "庫存量", example = "10")
    private Integer stockQty;

    @Temporal(TemporalType.DATE) // 指定一個日期時間欄位在對應到資料庫時，應該使用的資料類型。
    @Column(name = "CREATE_DATE", columnDefinition = "DATE", nullable = false)
    private LocalDate createDate;

    @Temporal(TemporalType.DATE) // 指定日期時間類型
    @Column(name = "MODIFIED_DATE", columnDefinition = "DATE", nullable = true)
    private LocalDate modifiedDate;

    @PrePersist // 在實體被持久化（新增）之前觸發
    public void prePersist() {
        this.createDate = LocalDate.now();
        this.modifiedDate = null; // 新增時不設置修改日期
    }

    @PreUpdate // 在實體被更新（修改）之前觸發
    public void preUpdate() {
        this.modifiedDate = LocalDate.now(); // 僅在更新時設置修改日期
    }

    // constructors

    public Product(String name, BigDecimal price, Integer saleStatus, Integer stockQty) {
        this.name = name;
        this.price = price;
        this.saleStatus = saleStatus;
        this.stockQty = stockQty;
    }

}
