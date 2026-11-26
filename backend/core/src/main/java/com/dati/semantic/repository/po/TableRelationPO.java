package com.dati.semantic.repository.po;

import com.dati.base.pojo.BaseResourcePO;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.io.Serial;

@Setter
@Getter
@FieldNameConstants
@Entity
@Table(name = "table_relation", uniqueConstraints = @UniqueConstraint(name = "uk_table_relation_join", columnNames = {"subject_id", "fact_table_id", "fact_column", "dimension_table_id", "dimension_column"}))
@AttributeOverride(name = "name", column = @Column(name = "name", length = 300))
public class TableRelationPO extends BaseResourcePO {
    @Serial private static final long serialVersionUID = 1L;
    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;
    @Column(name = "fact_table_id", nullable = false, length = 64)
    private String factTableId;
    @Column(name = "fact_column", nullable = false, length = 128)
    private String factColumn;
    @Column(name = "dimension_table_id", nullable = false, length = 64)
    private String dimensionTableId;
    @Column(name = "dimension_column", nullable = false, length = 128)
    private String dimensionColumn;
    @Column(nullable = false, length = 32)
    private String cardinality = "MANY_TO_ONE";
    @Column(length = 128)
    private String owner;
    @Column(nullable = false)
    private Integer revision = 1;
}
