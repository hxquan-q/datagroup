package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.param.InspectMetricArgs;
import com.dati.mcp.server.pojo.MetricInspection;
import com.dati.mcp.server.pojo.ToolTestData;
import com.dati.semantic.domain.model.MetricParameter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class InspectMetricExecutor implements ToolExecutor {

    @Override
    public McpToolType getToolType() {
        return McpToolType.INSPECT_METRIC;
    }

    @Override
    public ToolTestData execute(ToolExecutionContext ctx) {
        InspectMetricArgs args = ctx.args(InspectMetricArgs.class);
        McpServiceSnapshot.MetricDraft metric = PublishedMetricResolver.resolve(ctx.metrics(), args.metric());
        boolean structured = StructuredMetricSqlBuilder.isStructured(metric);

        MetricInspection.MetricDetail detail = new MetricInspection.MetricDetail(
            metric.metricId(), metric.subjectId(), metric.code(), metric.name(), metric.description(),
            metric.aliases(), metric.owner(), metric.grain(), metric.timeField(), metric.timeZone(),
            structured, metric.measureExpression(), metric.factSchema(), metric.factTableName(), metric.revision());

        List<MetricInspection.DimensionDetail> dimensions = metric.dimensionMappings().stream()
            .map(dimension -> new MetricInspection.DimensionDetail(
                dimension.getName(), dimension.getSchema(), dimension.getTableName(),
                dimension.getColumn(), dimension.getDescription()))
            .toList();

        List<MetricInspection.ParameterDetail> parameters = metric.parameters().stream()
            .map(this::toDetail)
            .toList();

        List<MetricInspection.JoinPath> joinPaths = ctx.relations().stream()
            .filter(relation -> touchesFactTable(relation, metric))
            .map(relation -> new MetricInspection.JoinPath(
                relation.factSchema(), relation.factTableName(), relation.factColumn(),
                relation.dimensionSchema(), relation.dimensionTableName(), relation.dimensionColumn(),
                relation.cardinality()))
            .toList();

        return new MetricInspection(detail, dimensions, parameters, joinPaths, example(metric, structured));
    }

    private boolean touchesFactTable(McpServiceSnapshot.TableRelationDraft relation, McpServiceSnapshot.MetricDraft metric) {
        if (metric.factTableName() == null) {
            return false;
        }
        boolean tableMatches = metric.factTableName().equalsIgnoreCase(relation.factTableName());
        boolean schemaMatches = metric.factSchema() == null || relation.factSchema() == null
            || metric.factSchema().equalsIgnoreCase(relation.factSchema());
        return tableMatches && schemaMatches;
    }

    private MetricInspection.ParameterDetail toDetail(MetricParameter parameter) {
        return new MetricInspection.ParameterDetail(parameter.getName(), parameter.getType(),
            parameter.isRequired(), parameter.getDefaultValue(), parameter.getDescription());
    }

    private MetricInspection.ExampleCall example(McpServiceSnapshot.MetricDraft metric, boolean structured) {
        Map<String, Object> invocation = new LinkedHashMap<>();
        invocation.put("metric", metric.metricId());
        if (!metric.allowedDimensions().isEmpty()) {
            invocation.put("dimensions", List.of(metric.allowedDimensions().getFirst()));
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (MetricParameter parameter : metric.parameters()) {
            if (parameter.isRequired()) {
                arguments.put(parameter.getName(),
                    parameter.getDefaultValue() != null ? parameter.getDefaultValue() : "<value>");
            }
        }
        if (structured) {
            arguments.putIfAbsent("start_time", "<start_time>");
            arguments.putIfAbsent("end_time", "<end_time>");
        }
        if (!arguments.isEmpty()) {
            invocation.put("arguments", arguments);
        }
        return new MetricInspection.ExampleCall("query_metric", invocation);
    }
}
