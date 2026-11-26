package com.dati.semantic.domain.model;

import com.dati.base.pojo.BaseResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ValidationCase extends BaseResource {
    private String subjectId;
    private String metricId;
    private String question;
    private Map<String, Object> arguments = new HashMap<>();
    private List<String> dimensions = new ArrayList<>();
    private String assertionType = "EXECUTION_SUCCESS";
    private String expectedValue;
    private String owner;
    private Boolean enabled = true;
    private String lastStatus;
    private String lastMessage;
    private Instant lastRunAt;
    private Long lastDurationMs;
    private Integer lastMetricRevision;
}
