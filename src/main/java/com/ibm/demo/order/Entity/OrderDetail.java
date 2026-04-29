package com.ibm.demo.order.Entity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ibm.demo.util.BaseEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@SuperBuilder
@Table(name = "ORDER_PRODUCT_DETAIL")
@SQLDelete(sql = "UPDATE ORDER_PRODUCT_DETAIL SET DELETED = 1,DELETED_AT = CURRENT_TIMESTAMP, VERSION = VERSION + 1 WHERE ID = ? AND VERSION = ?") // 使用樂觀鎖版本號來確保刪除操作的安全性
@SQLRestriction("DELETED = 0") // 只選擇未刪除的資料
@Schema(description = "訂單產品明細")
public class OrderDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_detail_seq_gen")
    @SequenceGenerator(name = "order_detail_seq_gen", sequenceName = "order_product_detail_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    @Schema(description = "訂單明細編號", example = "1")
    private Integer id;

    // 加入與 OrderInfo 的多對一關係映射
    @ManyToOne(fetch = FetchType.LAZY) // 延遲加載
    @JoinColumn(name = "ORDER_ID", referencedColumnName = "ID", nullable = false) // 外鍵欄位 ORDER_ID 指向 OrderInfo 的 ID
    @ToString.Exclude // 加在Entity中的OneToMany或ManyToOne的關聯上，否則會造成循環引用的問題，導致 StackOverflowError。
    @Schema(description = "訂單編號", example = "1")
    private OrderInfo orderInfo; // 指向所屬的 OrderInfo 物件

    @Schema(description = "商品編號", example = "1")
    @Column(name = "PRODUCT_ID", columnDefinition = "NUMBER(10)", nullable = false)
    private Integer productId;

    @Column(name = "QUANTITY", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "數量", example = "5")
    private Integer quantity;

    public void restore() {
        this.setDeleted(false);
        this.setDeletedAt(null);
    }
}