package com.dati.mcp.server.pojo;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.semantic.domain.model.MetricParameter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SemanticCatalog(List<SemanticMetric> metrics,
                              List<McpServiceSnapshot.TableRelationDraft> relations) implements ToolTestData {
    public SemanticCatalog(List<SemanticMetric> metrics) {
        this(metrics, List.of());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SemanticMetric(String metricId, String subjectId, String code, String name,
                                 String description, List<String> aliases, String owner,
                                 String grain, String timeField, String timeZone,
                                 List<String> allowedDimensions, List<MetricParameter> parameters,
                                 Integer revision, MetricCallExample callExample) {
        public SemanticMetric(String metricId, String subjectId, String code, String name,
                              String description, List<String> aliases, String owner,
                              String grain, String timeField, String timeZone,
                              List<String> allowedDimensions, List<MetricParameter> parameters,
                              Integer revision) {
            this(metricId, subjectId, code, name, description, aliases, owner, grain, timeField,
                timeZone, allowedDimensions, parameters, revision, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MetricCallExample(String metric, Map<String, Object> arguments, List<String> dimensions) {
    }
}
