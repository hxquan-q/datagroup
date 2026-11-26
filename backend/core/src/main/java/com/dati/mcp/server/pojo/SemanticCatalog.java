package com.dati.mcp.server.pojo;

import com.dati.semantic.domain.model.MetricParameter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SemanticCatalog(List<SemanticMetric> metrics) implements ToolTestData {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SemanticMetric(String metricId, String subjectId, String code, String name,
                                 String description, List<String> aliases, String owner,
                                 String grain, String timeField, String timeZone,
                                 List<String> allowedDimensions, List<MetricParameter> parameters,
                                 Integer revision) {
    }
}
