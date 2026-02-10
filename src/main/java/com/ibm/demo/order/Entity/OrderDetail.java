package com.ibm.demo.order.Entity;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ORDER_PRODUCT_DETAIL")
@Schema(description = "訂單產品明細")
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_detail_seq_gen")
    @SequenceGenerator(name = "order_detail_seq_gen", sequenceName = "order_product_detail_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    @Schema(description = "訂單明細編號", example = "1")
    private Integer id;

    // 加入與 OrderInfo 的多對一關係映射
    @ManyToOne(fetch = FetchType.LAZY) // 延遲加載
    @JoinColumn(name = "ORDER_ID", referencedColumnName = "ID", nullable = false) // 外鍵欄位 ORDER_ID 指向 OrderInfo 的 ID
    @ToString.Exclude //加在Entity中的OneToMany或ManyToOne的關聯上，否則會造成循環引用的問題，導致 StackOverflowError。
    @Schema(description = "訂單編號", example = "1")
    private OrderInfo orderInfo; // 指向所屬的 OrderInfo 物件

    @Schema(description = "商品編號", example = "1")
    @Column(name = "PRODUCT_ID", columnDefinition = "NUMBER(10)", nullable = false)
    private Integer productId;

    @Column(name = "QUANTITY", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "數量", example = "5")
    private Integer quantity;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    @Schema(description = "創建時間", example = "2024-06-01T12:00:00")
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "UPDATED_AT")
    @Schema(description = "更新時間", example = "2024-06-01T12:00:00")
    private LocalDateTime updatedAt;

    @Column(name = "DELETED", columnDefinition = "NUMBER(1)", nullable = false)
    @Schema(description = "是否刪除", example = "false")
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "DELETED_AT", columnDefinition = "TIMESTAMP")
    @Schema(description = "刪除時間", example = "2024-06-01T12:00:00")
    private LocalDateTime deletedAt;

    // constructor

    public OrderDetail(OrderInfo orderInfo, Integer productId, Integer quantity) {
        this.orderInfo = orderInfo;
        this.productId = productId;
        this.quantity = quantity;

    }
}