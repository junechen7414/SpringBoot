package com.ibm.demo.order;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ibm.demo.util.AuditMetadata;
import com.ibm.demo.util.SoftDeleteMetadata;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@EntityListeners(AuditingEntityListener.class) // 必須標在 @Entity；標在 @Embeddable 會被靜默忽略（見 AuditMetadata）
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

    // 雙向關聯（配對 OrderDetail.orderInfo）：判準是生命週期綁定 —— 明細隨 order 而生滅，
    // 沒有跨 order 查明細的需求。cascade 只留實際用到的 PERSIST/MERGE，REMOVE 由 orphanRemoval 涵蓋。
    // 完整取捨（收益、代價、為何不改單向）見 筆記.md「要不要做『雙向』關聯？」一節。
    @OneToMany(mappedBy = "orderInfo", cascade = { CascadeType.PERSIST, CascadeType.MERGE }, orphanRemoval = true)
    @BatchSize(size = 50) // 列表分頁載入時，把多筆 order 的 lazy orderDetails 併成少量 IN 查詢，緩解 N+1
    @ToString.Exclude // 避免Entity中有OneToMany或ManyToOne關聯時，因為循環引用導致 StackOverflowError。
    @Builder.Default // @Builder 走 all-args constructor 會蓋掉欄位初始值，少了這行 builder 路徑會拿到 null
    private List<OrderDetail> orderDetails = new ArrayList<>();
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

    /**
     * 掛載明細的唯一入口：同步雙向關聯的兩端（JPA 不會自動維護，只設一端會讓物件圖與 DB 不一致）。
     * 刻意不提供成對的 removeOrderDetail，理由見 筆記.md「要不要做『雙向』關聯？」一節。
     */
    public void addOrderDetail(OrderDetail detail) {
        this.orderDetails.add(detail);
        detail.setOrderInfo(this);
    }

    public void restore() {
        this.softDeleteMetadata.setDeleted(false);
        this.softDeleteMetadata.setDeletedAt(null);
    }
}
