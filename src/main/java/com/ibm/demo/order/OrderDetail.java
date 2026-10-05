package com.ibm.demo.order;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ibm.demo.util.AuditMetadata;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@EntityListeners(AuditingEntityListener.class) // 必須標在 @Entity；標在 @Embeddable 會被靜默忽略（見 AuditMetadata）
@Table(name = "ORDER_PRODUCT_DETAIL")
// 不做軟刪除（見 docs/adr/0001-soft-delete-only-for-termination.md）：更新訂單時被移除的明細由
// orphanRemoval 直接實體刪除。Hibernate 對 @Version entity 的預設刪除語句是 WHERE ID = ? AND VERSION = ?，
// 命中 0 列即拋 StaleObjectStateException，所以逐筆樂觀鎖仍在。
// 訂單被取消時明細原封不動地保留，跟著訂單一起封存（不會走到這裡）。
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_detail_seq_gen")
    @SequenceGenerator(name = "order_detail_seq_gen", sequenceName = "order_product_detail_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    private Integer id;

    // 關聯的擁有端（owning side）：ORDER_ID 這個外鍵欄位由本欄位決定。
    // 目前沒有任何地方從明細往上走訪 order；留著 @ManyToOne 是因為 OrderInfo 的
    // @OneToMany(mappedBy) 與 orphanRemoval 都要求擁有端存在。細節見 筆記.md「要不要做『雙向』關聯？」一節。
    @ManyToOne(fetch = FetchType.LAZY) // 延遲載入
    // ORDER_ID 是「本表」ORDER_PRODUCT_DETAIL 的外鍵欄位；不指定 name 會被推導成 order_info_id
    // （屬性名 + 對方 PK 欄位，再過 CamelCaseToUnderscoresNamingStrategy），與 V1 migration 不符
    // → ddl-auto: validate 啟動即失敗。被參考的目標欄位預設就是 OrderInfo 的 @Id（ORDER_INFO.ID），
    // 故不需 referencedColumnName。nullable 只影響 DDL 產生（本專案僅 openapi profile 會產 DDL）。
    @JoinColumn(name = "ORDER_ID", nullable = false)
    @ToString.Exclude // 避免Entity中有OneToMany或ManyToOne關聯時，因為循環引用導致 StackOverflowError。
    private OrderInfo orderInfo;

    @Column(name = "PRODUCT_ID", columnDefinition = "NUMBER(10)", nullable = false)
    private Integer productId;

    @Column(name = "QUANTITY", columnDefinition = "NUMBER(10)", nullable = false)
    private Integer quantity;

    // 組合：審計欄位
    @Embedded
    @Builder.Default
    private AuditMetadata auditMetadata = new AuditMetadata();

    // 樂觀鎖版本（@Version 不能在 @Embeddable 中使用，必須直接定義在實體類別）
    @Version
    @Column(name = "VERSION", columnDefinition = "NUMBER(10) DEFAULT 0", nullable = false)
    @Builder.Default
    private Integer version = 0;
}
