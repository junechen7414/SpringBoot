package com.ibm.demo.account;

import java.time.LocalDate; // 使用 LocalDate 對應 DATE 型別

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "ACCOUNT") // 指定對應的資料表名稱
@Schema(description = "帳號資訊")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq_gen")
    @SequenceGenerator(name = "account_seq_gen", sequenceName = "account_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)", nullable = false)
    @Schema(description = "帳⼾編號", example = "1")
    private Integer id;

    @Column(name = "NAME", columnDefinition = "NVARCHAR2(50)", nullable = false)
    @Schema(description = "帳戶名稱", example = "帳戶名稱")
    private String name;

    @Column(name = "STATUS", columnDefinition = "VARCHAR2(1)", nullable = false)
    @Schema(description = "啟用狀態", example = "Y")
    private String status;

    @Temporal(TemporalType.DATE) // 指定日期時間類型
    @Column(name = "CREATE_DATE", columnDefinition = "DATE", nullable = false)
    private LocalDate createDate;

    @Temporal(TemporalType.DATE) // 指定日期時間類型
    @Column(name = "MODIFIED_DATE", columnDefinition = "DATE", nullable = true)
    private LocalDate modifiedDate;

    @PrePersist
    public void prePersist() {
        this.createDate = LocalDate.now();
        this.modifiedDate = null; // 新增時不設置修改日期
    }

    @PreUpdate
    public void preUpdate() {
        this.modifiedDate = LocalDate.now(); // 僅在更新時設置修改日期
    }
    // constructors

    public Account(String name, String status) {
        this.name = name;
        this.status = status;
    }

}