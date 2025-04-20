package com.ibm.demo.account;

import java.time.LocalDateTime; // 使用 LocalDateTime 對應 DATE 型別
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ibm.demo.order_info.OrderInfo;

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
@Table(name = "ACCOUNT") // 指定對應的資料表名稱
@Schema(description = "帳號資訊")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq_gen")
    @SequenceGenerator(name = "account_seq_gen", sequenceName = "account_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "帳⼾編號", example = "1")
    private int id;

    @Column(name = "NAME", columnDefinition = "NVARCHAR2(50)", nullable = false)
    @Schema(description = "帳戶名稱", example = "帳戶名稱")
    private String name;

    @Column(name = "STATUS", columnDefinition = "VARCHAR2(1)", nullable = false)
    @Schema(description = "啟用狀態", example = "Y")
    private String status;

    @CreatedDate // 標記為創建日期欄位
    @Temporal(TemporalType.TIMESTAMP) // 指定日期時間類型
    @Column(name = "CREATE_DATE", columnDefinition = "DATE", nullable = false)
    private LocalDateTime createDate;

    @LastModifiedDate // 標記為更新日期欄位
    @Temporal(TemporalType.TIMESTAMP) // 指定日期時間類型
    @Column(name = "MODIFIED_DATE", columnDefinition = "DATE", nullable = true)
    private LocalDateTime modifiedDate;

    // 加入與 OrderInfo 的一對多關係映射
    // mappedBy = "account" 指的是在 OrderInfo Entity 中對應的屬性名稱
    // cascade = CascadeType.ALL 表示對 Account 的操作（如刪除）會級聯影響關聯的 OrderInfo
    // orphanRemoval = true 移除關聯的 OrderInfo 當它不再被 Account 引用時
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderInfo> orders; // 存放該帳號下的所有訂單

    // constructors

    public Account(String name, String status) {
        this.name = name;
        this.status = status;
    }

}