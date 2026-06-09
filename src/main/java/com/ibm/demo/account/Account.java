package com.ibm.demo.account;

import org.hibernate.annotations.SQLRestriction;

import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.util.AuditMetadata;
import com.ibm.demo.util.SoftDeleteMetadata;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@SQLRestriction("STATUS = 'Y' AND DELETED = false") // 只選擇啟用狀態且未被刪除的資料
@Table(name = "ACCOUNT") // 指定對應的資料表名稱
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq_gen")
    @SequenceGenerator(name = "account_seq_gen", sequenceName = "account_id_seq", allocationSize = 1)
    @Column(name = "ID", columnDefinition = "NUMBER(10)", nullable = false)
    private Integer id;

    @Column(name = "NAME", columnDefinition = "NVARCHAR2(50)", nullable = false)
    private String name;

    @Column(name = "STATUS", columnDefinition = "VARCHAR2(1)", nullable = false)
    private String status;

    // 組合：審計欄位
    @Embedded
    @Builder.Default
    private AuditMetadata auditMetadata = new AuditMetadata();

    // 組合：軟刪除欄位
    @Embedded
    @Builder.Default
    private SoftDeleteMetadata softDeleteMetadata = new SoftDeleteMetadata();

    // 樂觀鎖版本（@Version 不能在 @Embeddable 中使用，必須直接定義在實體類別）
    @Version
    @Column(name = "VERSION", columnDefinition = "NUMBER(10) DEFAULT 0", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public void restore() {
        this.softDeleteMetadata.setDeleted(false);
        this.softDeleteMetadata.setDeletedAt(null);
        this.setStatus(AccountStatus.ACTIVE.getCode());
    }
}