package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.ToolError;
import com.dati.mcp.domain.model.ToolParameter;
import com.dati.mcp.domain.model.param.QueryMetricArgs;
import com.dati.mcp.server.pojo.MetricExecution;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.ToolTestData;
import com.dati.semantic.domain.model.MetricParameter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class QueryMetricExecutor implements ToolExecutor {

    private final ParameterizedSqlExecutor parameterizedSqlExecutor;

    public QueryMetricExecutor(ParameterizedSqlExecutor parameterizedSqlExecutor) {
        this.parameterizedSqlExecutor = parameterizedSqlExecutor;
    }

    @Override
    public McpToolType getToolType() {
        return McpToolType.QUERY_METRIC;
    }

    @Override
    public ToolTestData execute(ToolExecutionContext ctx) {
        QueryMetricArgs args = ctx.args(QueryMetricArgs.class);
        McpServiceSnapshot.MetricDraft metric = PublishedMetricResolver.resolve(ctx.metrics(), args.metric());
        validateDimensions(metric, args.dimensions());
        if (args.arguments().containsKey("dimensions")) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID,
                "Pass dimensions in the top-level dimensions field, not inside arguments");
        }
        String sqlTemplate = metric.sqlTemplate();
        boolean structured = StructuredMetricSqlBuilder.isStructured(metric);
        Map<String, Object> argumentsWithDefaults = new HashMap<>(args.arguments());
        metric.parameters().forEach(parameter -> {
            if (parameter.getDefaultValue() != null && argumentsWithDefaults.get(parameter.getName()) == null) {
                argumentsWithDefaults.put(parameter.getName(), parameter.getDefaultValue());
            }
        });
        if (structured) {
            sqlTemplate = StructuredMetricSqlBuilder.build(metric, args.dimensions(), ctx.relations(), argumentsWithDefaults);
        }
        if (containsRawPlaceholder(sqlTemplate)) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID,
                "Metric SQL templates must use bound placeholders only; raw placeholders are not executable through query_metric");
        }

        Map<String, Object> parameters = new HashMap<>(args.arguments());
        if (!args.dimensions().isEmpty()) {
            parameters.put("dimensions", args.dimensions());
        }

        ToolConfig.ParamSqlConfig config = new ToolConfig.ParamSqlConfig();
        config.setDataSourceId(metric.dataSourceId());
        config.setSqlTemplate(sqlTemplate);
        List<ToolParameter> toolParameters = new java.util.ArrayList<>(
            metric.parameters().stream().map(this::toToolParameter).toList());
        if (structured) {
            addImplicitTimeParameter(toolParameters, "start_time");
            addImplicitTimeParameter(toolParameters, "end_time");
        }
        config.setParameters(toolParameters);
        config.setTimeout(metric.timeout());
        config.setMaxRows(metric.maxRows());

        ToolExecutionContext delegated = new ToolExecutionContext(
            ctx.serviceId(), McpToolType.PARAMETERIZED_SQL, config, parameters, ctx.scopeItems());
        SqlExecution execution = (SqlExecution) parameterizedSqlExecutor.execute(delegated);

        MetricExecution.MetricInfo info = new MetricExecution.MetricInfo(
            metric.metricId(), metric.subjectId(), metric.code(), metric.name(), metric.owner(),
            metric.grain(), metric.timeField(), metric.timeZone(), metric.allowedDimensions(), metric.revision());
        return new MetricExecution(info, parameters, args.dimensions(), execution);
    }

    private void validateDimensions(McpServiceSnapshot.MetricDraft metric, List<String> dimensions) {
        if (dimensions.isEmpty()) {
            return;
        }
        Set<String> allowed = new HashSet<>(metric.allowedDimensions());
        List<String> invalid = dimensions.stream().filter(dimension -> !allowed.contains(dimension)).toList();
        if (!invalid.isEmpty()) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID,
                "Dimensions are not allowed for metric " + metric.code() + ": " + String.join(", ", invalid));
        }
    }

    private void addImplicitTimeParameter(List<ToolParameter> parameters, String name) {
        boolean exists = parameters.stream().anyMatch(parameter -> name.equals(parameter.getName()));
        if (!exists) {
            parameters.add(new ToolParameter(name, "DateTime", false, null, "Structured metric time range bound"));
        }
    }

    private boolean containsRawPlaceholder(String template) {
        if (template == null) {
            return false;
        }
        for (int i = 0; i + 2 < template.length(); i++) {
            if (template.charAt(i) == '{' && template.charAt(i + 1) == '{' && template.charAt(i + 2) == '{'
                && (i == 0 || template.charAt(i - 1) != '\\')) {
                return true;
            }
        }
        return false;
    }

    private ToolParameter toToolParameter(MetricParameter parameter) {
        return new ToolParameter(parameter.getName(), parameter.getType(), parameter.isRequired(),
            parameter.getDefaultValue(), parameter.getDescription());
    }
}
