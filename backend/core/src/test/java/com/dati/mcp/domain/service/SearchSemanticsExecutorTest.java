package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.param.SearchSemanticsArgs;
import com.dati.mcp.server.pojo.SemanticCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SearchSemanticsExecutor tests")
class SearchSemanticsExecutorTest {

    private final SearchSemanticsExecutor executor = new SearchSemanticsExecutor();

    private McpServiceSnapshot.MetricDraft metric() {
        return new McpServiceSnapshot.MetricDraft(
            "metric-1", "subject-1", "ds-1", "quality_daily_fpy", "质量日报 FPY",
            "Quality daily first pass yield", List.of("FPY", "质量合格率"), "owner",
            "day", "FDate", "Asia/Shanghai", List.of("workshop"),
            "SELECT 1", List.of(), 30, 1000, 1);
    }

    @Test
    @DisplayName("finds metrics by alias without exposing SQL text")
    void findsMetricByAlias() {
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.SEARCH_SEMANTICS, new ToolConfig.SearchSemanticsConfig(),
            new SearchSemanticsArgs(List.of("合格率")), List.of(), List.of(metric()));

        SemanticCatalog result = (SemanticCatalog) executor.execute(ctx);

        assertThat(result.metrics()).hasSize(1);
        assertThat(result.metrics().getFirst().code()).isEqualTo("quality_daily_fpy");
        assertThat(result.metrics().getFirst().allowedDimensions()).containsExactly("workshop");
    }

    @Test
    @DisplayName("empty keywords list the published metric catalog")
    void emptyKeywordsListCatalog() {
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.SEARCH_SEMANTICS, new ToolConfig.SearchSemanticsConfig(),
            new SearchSemanticsArgs(List.of()), List.of(), List.of(metric()));

        SemanticCatalog result = (SemanticCatalog) executor.execute(ctx);

        assertThat(result.metrics()).hasSize(1);
    }
}
