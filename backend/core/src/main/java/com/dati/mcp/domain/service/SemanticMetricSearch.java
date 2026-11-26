package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.server.pojo.SemanticCatalog;
import com.dati.semantic.domain.model.MetricParameter;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SemanticMetricSearch {

    private SemanticMetricSearch() {
    }

    static List<SemanticCatalog.SemanticMetric> search(List<McpServiceSnapshot.MetricDraft> metrics,
                                                       List<String> rawKeywords, int maxResults) {
        List<String> keywords = rawKeywords == null ? List.of() : rawKeywords.stream()
            .map(keyword -> keyword == null ? "" : keyword.toLowerCase(Locale.ROOT).trim())
            .filter(keyword -> !keyword.isEmpty())
            .toList();
        return metrics.stream()
            .filter(metric -> keywords.isEmpty() || keywords.stream().anyMatch(keyword -> matches(metric, keyword)))
            .sorted(Comparator.comparing(McpServiceSnapshot.MetricDraft::code))
            .limit(maxResults)
            .map(SemanticMetricSearch::toSemanticMetric)
            .toList();
    }

    private static boolean matches(McpServiceSnapshot.MetricDraft metric, String keyword) {
        return contains(metric.code(), keyword)
            || contains(metric.name(), keyword)
            || contains(metric.description(), keyword)
            || metric.aliases().stream().anyMatch(alias -> contains(alias, keyword));
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static SemanticCatalog.SemanticMetric toSemanticMetric(McpServiceSnapshot.MetricDraft metric) {
        return new SemanticCatalog.SemanticMetric(
            metric.metricId(), metric.subjectId(), metric.code(), metric.name(), metric.description(),
            metric.aliases(), metric.owner(), metric.grain(), metric.timeField(), metric.timeZone(),
            metric.allowedDimensions(), metric.parameters(), metric.revision(), callExample(metric));
    }

    private static SemanticCatalog.MetricCallExample callExample(McpServiceSnapshot.MetricDraft metric) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (MetricParameter parameter : metric.parameters()) {
            if (parameter != null && parameter.isRequired()) {
                if (!supportsCopyableExample(parameter)) {
                    return null;
                }
                arguments.put(parameter.getName(), exampleValue(parameter));
            }
        }
        return new SemanticCatalog.MetricCallExample(metric.metricId(), arguments, List.of());
    }

    private static boolean supportsCopyableExample(MetricParameter parameter) {
        String type = parameter.getType() == null ? "String" : parameter.getType().toLowerCase(Locale.ROOT);
        return type.equals("string") || type.equals("number") || type.equals("integer")
            || type.equals("boolean") || type.equals("datetime");
    }

    private static Object exampleValue(MetricParameter parameter) {
        if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isBlank()) {
            return parameter.getDefaultValue();
        }
        String type = parameter.getType() == null ? "" : parameter.getType().toLowerCase(Locale.ROOT);
        String name = parameter.getName() == null ? "" : parameter.getName().toLowerCase(Locale.ROOT);
        if (type.contains("bool")) return true;
        if (type.contains("int") || type.contains("number") || type.contains("decimal") || type.contains("double")) return 1;
        if (name.contains("start") || name.contains("begin")) return "2026-01-01 00:00:00";
        if (name.contains("end") || name.contains("finish")) return "2026-01-31 23:59:59";
        if (type.contains("date") || type.contains("time") || name.contains("date")) return "2026-01-01";
        return "value";
    }
}
