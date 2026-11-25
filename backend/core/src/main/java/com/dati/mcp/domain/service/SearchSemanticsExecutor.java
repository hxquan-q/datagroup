package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.param.SearchSemanticsArgs;
import com.dati.mcp.server.pojo.SemanticCatalog;
import com.dati.mcp.server.pojo.ToolTestData;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class SearchSemanticsExecutor implements ToolExecutor {

    private static final int MAX_RESULTS = 20;

    @Override
    public McpToolType getToolType() {
        return McpToolType.SEARCH_SEMANTICS;
    }

    @Override
    public ToolTestData execute(ToolExecutionContext ctx) {
        SearchSemanticsArgs args = ctx.args(SearchSemanticsArgs.class);
        List<String> keywords = args.keywords().stream()
            .map(keyword -> keyword == null ? "" : keyword.toLowerCase(Locale.ROOT).trim())
            .filter(keyword -> !keyword.isEmpty())
            .toList();

        List<SemanticCatalog.SemanticMetric> metrics = ctx.metrics().stream()
            .filter(metric -> keywords.isEmpty() || keywords.stream().anyMatch(keyword -> matches(metric, keyword)))
            .sorted(Comparator.comparing(McpServiceSnapshot.MetricDraft::code))
            .limit(MAX_RESULTS)
            .map(this::toSemanticMetric)
            .toList();
        return new SemanticCatalog(metrics);
    }

    private boolean matches(McpServiceSnapshot.MetricDraft metric, String keyword) {
        return contains(metric.code(), keyword)
            || contains(metric.name(), keyword)
            || contains(metric.description(), keyword)
            || metric.aliases().stream().anyMatch(alias -> contains(alias, keyword));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private SemanticCatalog.SemanticMetric toSemanticMetric(McpServiceSnapshot.MetricDraft metric) {
        return new SemanticCatalog.SemanticMetric(
            metric.metricId(), metric.subjectId(), metric.code(), metric.name(), metric.description(),
            metric.aliases(), metric.owner(), metric.grain(), metric.timeField(), metric.timeZone(),
            metric.allowedDimensions(), metric.parameters(), metric.revision());
    }
}
