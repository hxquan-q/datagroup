package com.dati.semantic.repository.po;

import com.dati.base.pojo.BaseResourcePO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@FieldNameConstants
@Entity
@Table(name = "validation_case")
public class ValidationCasePO extends BaseResourcePO {
    @Serial private static final long serialVersionUID = 1L;
    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;
    @Column(name = "metric_id", nullable = false, length = 64)
    private String metricId;
    @Column(nullable = false, length = 1024)
    private String question;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> arguments = new HashMap<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> dimensions = new ArrayList<>();
    @Column(name = "assertion_type", nullable = false, length = 64)
    private String assertionType = "EXECUTION_SUCCESS";
    @Column(name = "expected_value", length = 1024)
    private String expectedValue;
    @Column(length = 128)
    private String owner;
    @Column(nullable = false)
    private Boolean enabled = true;
    @Column(name = "last_status", length = 32)
    private String lastStatus;
    @Column(name = "last_message", length = 2048)
    private String lastMessage;
    @Column(name = "last_run_at")
    private Instant lastRunAt;
    @Column(name = "last_duration_ms")
    private Long lastDurationMs;
    @Column(name = "last_metric_revision")
    private Integer lastMetricRevision;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_evidence", columnDefinition = "json")
    private Map<String, Object> lastEvidence = new HashMap<>();
    @Version
    @Column(nullable = false)
    private long version;
}
