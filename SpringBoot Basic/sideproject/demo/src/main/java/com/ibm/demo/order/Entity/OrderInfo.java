package com.ibm.demo.order.Entity;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
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
    private List<OrderDetail> orderDetails; // 存放該訂單下的所有產品明細

    @PrePersist
    public void prePersist() {
        this.createDate = LocalDate.now();
        this.modifiedDate = null; // 新增時不設置修改日期
    }

    @PreUpdate
    public void preUpdate() {
        this.modifiedDate = LocalDate.now(); // 僅在更新時設置修改日期
    }

    // helper
    public void addOrderDetail(OrderDetail detail) {
        orderDetails.add(detail);
        detail.setOrderInfo(this); // 確保 OrderDetail 這邊的關聯也被設定
    }

    public void removeOrderDetail(OrderDetail detail) {
        orderDetails.remove(detail);
        detail.setOrderInfo(null); // 確保 OrderDetail 這邊的關聯被中斷
    }

}