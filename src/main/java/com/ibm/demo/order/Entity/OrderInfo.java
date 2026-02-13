package com.ibm.demo.order.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
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
@EntityListeners(AuditingEntityListener.class)
@Entity
@SQLDelete(sql = "UPDATE ORDER_INFO SET DELETED = 1,DELETED_AT = CURRENT_TIMESTAMP,STATUS = 1003 WHERE ID = ?")
@SQLRestriction("DELETED = 0 AND STATUS=1001") // 只選擇未刪除的資料
@Builder
@Table(name = "ORDER_INFO") // 指定對應的資料表名稱
@Schema(description = "訂單資訊")
public class OrderInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq_gen")
    @SequenceGenerator(name = "order_seq_gen", sequenceName = "order_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    @Schema(description = "訂單編號", example = "1")
    private Integer id;

    @Column(name = "ACCOUNT_ID", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "使用者編號", example = "1")
    private Integer accountId;

    @Column(name = "STATUS", columnDefinition = "NUMBER(4)", nullable = false)
    @Schema(description = "訂單狀態", example = "1001")
    private Integer status;

    @Temporal(TemporalType.DATE) // 指定日期時間類型
    @Column(name = "CREATE_DATE", columnDefinition = "DATE", nullable = false)
    private LocalDate createDate;

    @Temporal(TemporalType.DATE) // 指定日期時間類型
    @Column(name = "MODIFIED_DATE", columnDefinition = "DATE", nullable = true)
    private LocalDate modifiedDate;

    @OneToMany(mappedBy = "orderInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude // 加在Entity中的OneToMany或ManyToOne的關聯上，否則會造成循環引用的問題，導致 StackOverflowError。
    private List<OrderDetail> orderDetails; // 存放該訂單下的所有產品明細

    @Column(name = "DELETED", columnDefinition = "NUMBER(1)", nullable = false)
    @Schema(description = "是否刪除", example = "false")
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "DELETED_AT", columnDefinition = "TIMESTAMP")
    @Schema(description = "刪除時間", example = "2024-06-01T12:00:00")
    private LocalDateTime deletedAt;

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
    }

    @PrePersist
    public void prePersist() {
        this.createDate = LocalDate.now();
        this.modifiedDate = null; // 新增時不設置修改日期
    }

    @PreUpdate
    public void preUpdate() {
        this.modifiedDate = LocalDate.now(); // 僅在更新時設置修改日期
    }
}