package com.dati.datasource.repository.po;

import com.dati.base.pojo.BaseResourcePO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.io.Serial;

@Setter
@Getter
@FieldNameConstants
@Entity
@Table(name = "table_foreign_key")
public class TableForeignKeyPO extends BaseResourcePO {
    @Serial private static final long serialVersionUID = 1L;

    @Column(length = 64, nullable = false)
    private String datasourceId;

    @Column(length = 64, nullable = false)
    private String tableId;

    @Column(length = 128, nullable = false)
    private String columnName;

    @Column(length = 128)
    private String refSchema;

    @Column(length = 128, nullable = false)
    private String refTable;

    @Column(length = 128, nullable = false)
    private String refColumn;

    @Column(length = 32)
    private String onUpdate;

    @Column(length = 32)
    private String onDelete;

    @Column(nullable = false)
    private Short keySeq = 1;
}
