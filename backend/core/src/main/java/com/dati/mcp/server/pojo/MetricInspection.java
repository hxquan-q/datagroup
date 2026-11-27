package com.dati.mcp.server.pojo;

import java.util.List;
import java.util.Map;

public record MetricInspection(
    MetricDetail metric,
    List<DimensionDetail> dimensions,
    List<ParameterDetail> parameters,
    List<JoinPath> joinPaths,
    ExampleCall example
) implements ToolTestData {

    public record MetricDetail(
        String metricId,
        String subjectId,
        String code,
        String name,
        String description,
        List<String> aliases,
        String owner,
        String grain,
        String timeField,
        String timeZone,
        boolean structured,
        String measureExpression,
        String factSchema,
        String factTableName,
        Integer revision
    ) {
    }

    public record DimensionDetail(String name, String schema, String tableName, String column, String description) {
    }

    public record ParameterDetail(String name, String type, boolean required, String defaultValue, String description) {
    }

    public record JoinPath(String factSchema, String factTable, String factColumn,
                           String dimensionSchema, String dimensionTable, String dimensionColumn,
                           String cardinality) {
    }

    public record ExampleCall(String tool, Map<String, Object> arguments) {
    }
}
