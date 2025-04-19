package com.ibm.demo.order_info;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.ibm.demo.account.Account;
import com.ibm.demo.order_product_detail.OrderProductDetail;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "ORDER_INFO") // 指定對應的資料表名稱
@Schema(description = "訂單資訊")
public class OrderInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq_gen")
    @SequenceGenerator(name = "order_seq_gen", sequenceName = "order_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    @Schema(description = "訂單編號", example = "1")
    private int id;

    @Column(name = "STATUS", columnDefinition = "NUMBER(4)")
    @Schema(description = "訂單狀態", example = "1001")
    private int status;

    @Column(name = "TOTAL_AMOUNT", columnDefinition = "NUMBER(12,4)")
    @Schema(description = "訂單總金額", example = "1234.56")
    private BigDecimal totalAmount;

    @Column(name = "CREATE_DATE", columnDefinition = "DATE")
    @Schema(description = "建立日期", example = "2025-01-01T10:30:00")
    private LocalDateTime createDate;

    @Column(name = "MODIFIED_DATE", columnDefinition = "DATE", nullable = true)
    @Schema(description = "修改日期", example = "2025-01-02T11:00:00")
    private LocalDateTime modifiedDate;

    @ManyToOne(fetch = FetchType.LAZY) // 延遲加載，按需載入 Account 資訊
    @JoinColumn(name = "ACCOUNT_ID", referencedColumnName = "ID")
    @Schema(description = "使用者編號",example="1")
    private Account account; // 指向所屬的 Account 物件

    @OneToMany(mappedBy = "orderInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderProductDetail> orderDetails; // 存放該訂單下的所有產品明細

    // No-argument constructor
    public OrderInfo() {
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public List<OrderProductDetail> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(List<OrderProductDetail> orderDetails) {
        this.orderDetails = orderDetails;
    }

}