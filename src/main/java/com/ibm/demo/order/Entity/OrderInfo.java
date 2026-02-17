package com.ibm.demo.order.Entity;

import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ibm.demo.util.BaseEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@SuperBuilder
@Entity
@SQLDelete(sql = "UPDATE ORDER_INFO SET DELETED = 1,DELETED_AT = CURRENT_TIMESTAMP,STATUS = 1003 WHERE ID = ?")
@SQLRestriction("DELETED = 0 AND STATUS=1001") // 只選擇未刪除的資料
@Table(name = "ORDER_INFO") // 指定對應的資料表名稱
@Schema(description = "訂單資訊")
public class OrderInfo extends BaseEntity {

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

    @OneToMany(mappedBy = "orderInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude // 加在Entity中的OneToMany或ManyToOne的關聯上，否則會造成循環引用的問題，導致 StackOverflowError。
    private List<OrderDetail> orderDetails; // 存放該訂單下的所有產品明細

    // 移除原本手寫的 @Builder 建構子，SuperBuilder 會自動處理 accountId 與 status

    public void restore() {
        this.setDeleted(false);
        this.setDeletedAt(null);
    }
}