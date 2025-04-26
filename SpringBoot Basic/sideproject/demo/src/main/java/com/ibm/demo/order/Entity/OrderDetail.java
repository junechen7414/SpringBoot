package com.ibm.demo.order.Entity;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ibm.demo.product.Product;

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

    // @Column(name = "ORDER_ID", columnDefinition = "NUMBER(10)")
    // @Schema(description = "關聯的訂單ID", example = "1")
    // private Integer orderId;

    // @Column(name = "PRODUCT_ID", columnDefinition = "NUMBER(10)")
    // @Schema(description = "關聯的產品ID", example = "1")
    // private Integer productId;

    // 加入與 OrderInfo 的多對一關係映射
    @ManyToOne(fetch = FetchType.LAZY) // 延遲加載
    @JoinColumn(name = "ORDER_ID", referencedColumnName = "ID", nullable = false) // 外鍵欄位 ORDER_ID 指向 OrderInfo 的 ID
    @Schema(description = "訂單編號", example = "1")
    private OrderInfo orderInfo; // 指向所屬的 OrderInfo 物件

    // 加入與 Product 的多對一關係映射
    @ManyToOne(fetch = FetchType.LAZY) // 延遲加載
    @JoinColumn(name = "PRODUCT_ID", referencedColumnName = "ID", nullable = false) // 外鍵欄位 PRODUCT_ID 指向 Product 的 ID
    @Schema(description = "商品編號", example = "1")
    private Product product; // 指向關聯的 Product 物件

    @Column(name = "QUANTITY", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "數量", example = "5")
    private Integer quantity;

    @Column(name = "PRICE", columnDefinition = "NUMBER(12,4)", nullable = false)
    @Schema(description = "商品價格", example = "1234.56")
    private BigDecimal price;

    // constructor
    public OrderDetail(OrderInfo orderInfo, Product product, Integer quantity,BigDecimal price) {
        this.orderInfo = orderInfo;
        this.product = product;
        this.quantity = quantity;
        this.price = price;
    }
}