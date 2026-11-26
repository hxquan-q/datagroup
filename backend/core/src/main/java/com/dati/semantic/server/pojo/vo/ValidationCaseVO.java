package com.dati.semantic.server.pojo.vo;

import com.dati.base.pojo.BaseResourceVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ValidationCaseVO extends BaseResourceVO {
    private String subjectId;
    private String metricId;
    private String question;
    private Map<String, Object> arguments = new HashMap<>();
    private List<String> dimensions = new ArrayList<>();
    private String assertionType;
    private String expectedValue;
    private String owner;
    private Boolean enabled;
    private String lastStatus;
    private String lastMessage;
    private Instant lastRunAt;
    private Long lastDurationMs;
    private Integer lastMetricRevision;
    private Map<String, Object> lastEvidence = new HashMap<>();
}
