package com.dati.semantic.domain.model;

import com.dati.base.pojo.BaseResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ValidationCase extends BaseResource {
    private String subjectId;
    private String metricId;
    private String question;
    private Map<String, Object> arguments;
    private List<String> dimensions;
    private String assertionType;
    private String expectedValue;
    private String owner;
    private Boolean enabled;
    private String lastStatus;
    private String lastMessage;
    private Instant lastRunAt;
    private Long lastDurationMs;
    private Integer lastMetricRevision;
    private Map<String, Object> lastEvidence;
}
