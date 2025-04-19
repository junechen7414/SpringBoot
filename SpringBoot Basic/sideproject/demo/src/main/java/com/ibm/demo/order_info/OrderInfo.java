package main.java.com.ibm.demo.order_info;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects; // Import Objects for equals and hashCode

import javax.persistence.CascadeType; // Import CascadeType
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType; // Import FetchType
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn; // Import JoinColumn
import javax.persistence.ManyToOne; // Import ManyToOne
import javax.persistence.OneToMany; // Import OneToMany
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;
import main.java.com.ibm.demo.account.Account;
import main.java.com.ibm.demo.order_product_detail.OrderProductDetail;

@Entity
@Table(name = "ORDER_INFO") // 指定對應的資料表名稱
@Schema(description = "訂單資訊")
public class OrderInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq_gen")
    @SequenceGenerator(name = "order_seq_gen", sequenceName = "order_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    @Schema(description = "從1開始自動生成的訂單ID", example = "1")
    private int id;

    @Column(name = "STATUS", columnDefinition = "NUMBER(4)")
    @Schema(description = "訂單狀態", example = "1000")
    private int status;

    @Column(name = "TOTAL_AMOUNT", columnDefinition = "NUMBER(12,4)")
    @Schema(description = "訂單總金額", example = "1234.56")
    private BigDecimal totalAmount;

    @Column(name = "CREATE_DATE", columnDefinition = "DATE")
    @Schema(description = "訂單建立日期", example = "2025-01-01T10:30:00")
    private LocalDateTime createDate;

    @Column(name = "MODIFIED_DATE", columnDefinition = "DATE", nullable = true)
    @Schema(description = "訂單更新日期", example = "2025-01-02T11:00:00")
    private LocalDateTime modifiedDate;

    @ManyToOne(fetch = FetchType.LAZY) // 延遲加載，按需載入 Account 資訊
    @JoinColumn(name = "ACCOUNT_ID", referencedColumnName = "ID")
    @Schema(description = "所屬帳號")
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