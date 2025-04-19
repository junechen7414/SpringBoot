package com.ibm.demo.account;
import java.time.LocalDateTime; // 使用 LocalDateTime 對應 DATE 型別
import java.util.List;

import com.ibm.demo.order_info.OrderInfo;

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

@Entity
@Table(name = "ACCOUNT") // 指定對應的資料表名稱
@Schema(description = "帳號資訊")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq_gen")
    @SequenceGenerator(name = "account_seq_gen", sequenceName = "account_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)")
    @Schema(description = "帳⼾編號", example = "1")
    private int id;

    @Column(name = "NAME", columnDefinition = "NVARCHAR2(50)")
    @Schema(description = "帳戶名稱", example = "帳戶名稱")
    private String name;

    @Column(name = "STATUS", columnDefinition = "VARCHAR2(1)")
    @Schema(description = "啟用狀態", example = "Y")
    private String status;

    @Column(name = "CREATE_DATE", columnDefinition = "DATE")
    @Schema(description = "建立日期", example = "2025-01-01T10:30:00")
    private LocalDateTime createDate;

    @Column(name = "MODIFIED_DATE", columnDefinition = "DATE", nullable = true)
    @Schema(description = "修改日期", example = "2025-01-02T11:00:00")
    private LocalDateTime modifiedDate;

    // 加入與 OrderInfo 的一對多關係映射
    // mappedBy = "account" 指的是在 OrderInfo Entity 中對應的屬性名稱
    // cascade = CascadeType.ALL 表示對 Account 的操作（如刪除）會級聯影響關聯的 OrderInfo
    // orphanRemoval = true 移除關聯的 OrderInfo 當它不再被 Account 引用時
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderInfo> orders; // 存放該帳號下的所有訂單

    // No-argument constructor
    public Account() {
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public List<OrderInfo> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderInfo> orders) {
        this.orders = orders;
    }

}