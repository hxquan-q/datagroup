package com.dati.semantic.repository.po;

import com.dati.base.pojo.BaseResourcePO;
import com.dati.semantic.domain.model.MetricDimension;
import com.dati.semantic.domain.model.MetricParameter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@FieldNameConstants
@Entity
@Table(name = "metric", uniqueConstraints = {
    @UniqueConstraint(name = "uk_metric_subject_code", columnNames = {"subject_id", "code"})
})
public class MetricPO extends BaseResourcePO {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;

    @Column(name = "datasource_id", nullable = false, length = 64)
    private String dataSourceId;

    @Column(nullable = false, length = 128)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> aliases = new ArrayList<>();

    @Column(length = 128)
    private String owner;

    @Column(length = 128)
    private String grain;

    @Column(name = "time_field", length = 128)
    private String timeField;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_dimensions", columnDefinition = "json")
    private List<String> allowedDimensions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimension_mappings", columnDefinition = "json")
    private List<MetricDimension> dimensionMappings = new ArrayList<>();

    @Column(name = "measure_expression", columnDefinition = "text")
    private String measureExpression;

    @Column(name = "fact_schema", length = 128)
    private String factSchema;

    @Column(name = "fact_table_name", length = 128)
    private String factTableName;

    @Column(name = "sql_template", columnDefinition = "text")
    private String sqlTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<MetricParameter> parameters = new ArrayList<>();

    @Column(nullable = false)
    private Integer timeout = 30;

    @Column(name = "max_rows", nullable = false)
    private Integer maxRows = 1000;

    @Column(nullable = false)
    private Integer revision = 1;
}
