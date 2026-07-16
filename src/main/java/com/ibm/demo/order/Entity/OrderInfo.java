package com.ibm.demo.order.Entity;

import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import com.ibm.demo.util.AuditMetadata;
import com.ibm.demo.util.SoftDeleteMetadata;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Builder
@Entity
@SQLRestriction("DELETED = false AND STATUS=1001") // 只選擇未刪除且已確認的訂單
@Table(name = "ORDER_INFO") // 指定對應的資料表名稱
public class OrderInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq_gen")
    @SequenceGenerator(name = "order_seq_gen", sequenceName = "order_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    private Integer id;

    @Column(name = "ACCOUNT_ID", columnDefinition = "NUMBER(10)", nullable = false)
    private Integer accountId;

    @Column(name = "STATUS", columnDefinition = "NUMBER(4)", nullable = false)
    private Integer status;

    @OneToMany(mappedBy = "orderInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50) // 列表分頁載入時，把多筆 order 的 lazy orderDetails 併成少量 IN 查詢，緩解 N+1
    @ToString.Exclude // 避免Entity中有OneToMany或ManyToOne關聯時，因為循環引用導致 StackOverflowError。
    private List<OrderDetail> orderDetails; // 建立雙向關聯，方便查詢
    // 改用組合（@Embedded 元數據）後已無 @MappedSuperclass 父類，直接用類別上的 @Builder 即可（不需 @SuperBuilder）

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
