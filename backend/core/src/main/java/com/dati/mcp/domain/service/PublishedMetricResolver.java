package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.ToolError;

import java.util.List;
import java.util.Locale;

/** Resolves a user-supplied metric reference against the published snapshot catalog. */
public final class PublishedMetricResolver {

    private PublishedMetricResolver() {
    }

    public static McpServiceSnapshot.MetricDraft resolve(List<McpServiceSnapshot.MetricDraft> metrics, String key) {
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

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
