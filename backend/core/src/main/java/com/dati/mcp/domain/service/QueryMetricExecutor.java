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
import java.util.Locale;
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
        McpServiceSnapshot.MetricDraft metric = resolveMetric(ctx.metrics(), args.metric());
        validateDimensions(metric, args.dimensions());
        if (args.arguments().containsKey("dimensions")) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID,
                "Pass dimensions in the top-level dimensions field, not inside arguments");
        }
        if (containsRawPlaceholder(metric.sqlTemplate())) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID,
                "Metric SQL templates must use bound placeholders only; raw placeholders are not executable through query_metric");
        }

        Map<String, Object> parameters = new HashMap<>(args.arguments());
        if (!args.dimensions().isEmpty()) {
            parameters.put("dimensions", args.dimensions());
        }

        ToolConfig.ParamSqlConfig config = new ToolConfig.ParamSqlConfig();
        config.setDataSourceId(metric.dataSourceId());
        config.setSqlTemplate(metric.sqlTemplate());
        config.setParameters(metric.parameters().stream().map(this::toToolParameter).toList());
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

    private McpServiceSnapshot.MetricDraft resolveMetric(List<McpServiceSnapshot.MetricDraft> metrics, String key) {
        List<McpServiceSnapshot.MetricDraft> exact = metrics.stream()
            .filter(metric -> key.equals(metric.metricId()) || key.equals(metric.code()))
            .toList();
        if (exact.size() == 1) {
            return exact.getFirst();
        }
        if (exact.size() > 1) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID,
                "Metric reference is ambiguous: " + key + "; use the metric ID");
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        List<McpServiceSnapshot.MetricDraft> matches = metrics.stream()
            .filter(metric -> normalized.equals(lower(metric.name()))
                || metric.aliases().stream().anyMatch(alias -> normalized.equals(lower(alias))))
            .toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.size() > 1) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID,
                "Metric reference is ambiguous: " + key + "; use the stable metric code");
        }
        throw new ToolExecuteException(ToolError.PARAM_INVALID,
            "Metric not found in published semantic catalog: " + key);
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

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private ToolParameter toToolParameter(MetricParameter parameter) {
        return new ToolParameter(parameter.getName(), parameter.getType(), parameter.isRequired(),
            parameter.getDefaultValue(), parameter.getDescription());
    }
}
