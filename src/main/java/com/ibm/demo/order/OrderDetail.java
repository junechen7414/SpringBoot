package com.ibm.demo.order;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.jdbc.Expectation;

import com.ibm.demo.util.AuditMetadata;
import com.ibm.demo.util.SoftDeleteMetadata;

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
// orphanRemoval 觸發的實體 DELETE 改寫為軟刪 UPDATE，與 deleteOrder / soft-delete-everywhere 原則一致。
// @Version entity 的標準 delete 參數會依序 (id, version) 綁到自訂 SQL，故 SQL 必須剛好兩個 ?：WHERE ID = ? AND VERSION = ?。
// verify = RowCount：自訂 @SQLDelete 預設不檢查影響列數；若不指定，version 過期命中 0 列會「靜默不軟刪」。
// 加上 RowCount 後，命中列數 != 1 → StaleStateException（保留樂觀鎖，符合 DBAssertion 精神）。
// 同步遞增 VERSION、寫入 DELETED_AT，與 softDeleteByOrderId 語義相同。
@SQLDelete(sql = "UPDATE ORDER_PRODUCT_DETAIL "
        + "SET DELETED = true, DELETED_AT = CURRENT_TIMESTAMP, VERSION = VERSION + 1 "
        + "WHERE ID = ? AND VERSION = ?", verify = Expectation.RowCount.class)
@SQLRestriction("DELETED = false") // 只選擇未刪除的訂單明細
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_detail_seq_gen")
    @SequenceGenerator(name = "order_detail_seq_gen", sequenceName = "order_product_detail_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    private Integer id;

    // 多對一關聯到OrderInfo 的外鍵欄位
    @ManyToOne(fetch = FetchType.LAZY) // 延遲載入
    @JoinColumn(name = "ORDER_ID", referencedColumnName = "ID", nullable = false) // 映射到 ORDER_ID 和 OrderInfo 的ID
    @ToString.Exclude // 避免Entity中有OneToMany或ManyToOne關聯時，因為循環引用導致 StackOverflowError。
    private OrderInfo orderInfo; // 建立雙向關聯到 OrderInfo 物件

    @Column(name = "PRODUCT_ID", columnDefinition = "NUMBER(10)", nullable = false)
    private Integer productId;

    @Column(name = "QUANTITY", columnDefinition = "NUMBER(10)", nullable = false)
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
    @Builder.Default
    private Integer version = 0;

    public void restore() {
        this.softDeleteMetadata.setDeleted(false);
        this.softDeleteMetadata.setDeletedAt(null);
    }
}
