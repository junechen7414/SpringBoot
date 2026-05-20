package com.ibm.demo.order.Entity;

import org.hibernate.annotations.SQLRestriction;

import com.ibm.demo.util.AuditMetadata;
import com.ibm.demo.util.SoftDeleteMetadata;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "ORDER_PRODUCT_DETAIL")
@SQLRestriction("DELETED = false") // 只選擇未刪除的訂單明細
@Schema(description = "訂單明細資訊")
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_detail_seq_gen")
    @SequenceGenerator(name = "order_detail_seq_gen", sequenceName = "order_product_detail_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    @Schema(description = "訂單明細編號", example = "1")
    private Integer id;

    // 多對一關聯到OrderInfo 的外鍵欄位
    @ManyToOne(fetch = FetchType.LAZY) // 延遲載入
    @JoinColumn(name = "ORDER_ID", referencedColumnName = "ID", nullable = false) // 映射到 ORDER_ID 和 OrderInfo 的ID
    @ToString.Exclude // 避免Entity中有OneToMany或ManyToOne關聯時，因為循環引用導致 StackOverflowError。
    @Schema(description = "訂單編號", example = "1")
    private OrderInfo orderInfo; // 建立雙向關聯到 OrderInfo 物件

    @Schema(description = "商品編號", example = "1")
    @Column(name = "PRODUCT_ID", columnDefinition = "NUMBER(10)", nullable = false)
    private Integer productId;

    @Column(name = "QUANTITY", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "數量", example = "5")
    private Integer quantity;

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
    }
}
