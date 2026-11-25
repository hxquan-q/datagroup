package com.dati.mcp.server.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricExecution(MetricInfo metric, Map<String, Object> parameters,
                              List<String> dimensions, SqlExecution execution) implements ToolTestData {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MetricInfo(String metricId, String subjectId, String code, String name,
                             String owner, String grain, String timeField, String timeZone,
                             List<String> allowedDimensions, Integer revision) {
    }
}
