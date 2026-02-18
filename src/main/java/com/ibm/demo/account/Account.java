package com.ibm.demo.account;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ibm.demo.enums.AccountStatus;
import com.ibm.demo.util.BaseEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@SuperBuilder
@SQLDelete(sql = "UPDATE ACCOUNT SET DELETED = 1, DELETED_AT = CURRENT_TIMESTAMP, STATUS = 'N' WHERE ID = ?") // 軟刪除，將
                                                                                                              // STATUS
                                                                                                              // 設為 'N'
@SQLRestriction("STATUS = 'Y' AND DELETED = 0") // 只選擇啟用狀態且未被刪除的資料
@Table(name = "ACCOUNT") // 指定對應的資料表名稱
@Schema(description = "帳號資訊")
public class Account extends BaseEntity {

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

    public void restore() {
        this.setDeleted(false);
        this.setStatus(AccountStatus.ACTIVE.getCode());
    }
}
