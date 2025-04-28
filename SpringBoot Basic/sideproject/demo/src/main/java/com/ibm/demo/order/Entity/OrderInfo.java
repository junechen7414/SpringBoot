package com.ibm.demo.order.Entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
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
@EntityListeners(AuditingEntityListener.class)
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

    @CreatedDate // 標記為創建日期欄位
    @Temporal(TemporalType.DATE) // 指定日期時間類型
    @Column(name = "CREATE_DATE", columnDefinition = "DATE", nullable = false)
    private LocalDate createDate;

    @LastModifiedDate // 標記為更新日期欄位
    @Temporal(TemporalType.DATE) // 指定日期時間類型
    @Column(name = "MODIFIED_DATE", columnDefinition = "DATE", nullable = true)
    private LocalDate modifiedDate;

    @OneToMany(mappedBy = "orderInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> orderDetails; // 存放該訂單下的所有產品明細

    // helper
    public void addOrderDetail(OrderDetail detail) {
        orderDetails.add(detail);
        detail.setOrderInfo(this); // 確保 OrderDetail 這邊的關聯也被設定
    }

    public void removeOrderDetail(OrderDetail detail) {
        orderDetails.remove(detail);
        detail.setOrderInfo(null); // 確保 OrderDetail 這邊的關聯被中斷
    }

    public BigDecimal calculateTotalAmount() {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderDetail orderDetail : this.orderDetails) {
            BigDecimal productPrice = orderDetail.getPrice();
            Integer quantity = orderDetail.getQuantity();
            totalAmount = totalAmount.add(productPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        return totalAmount;
    }

}