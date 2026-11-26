package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.param.SearchSemanticsArgs;
import com.dati.mcp.server.pojo.SemanticCatalog;
import com.dati.mcp.server.pojo.ToolTestData;
import org.springframework.stereotype.Component;

import java.util.List;

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
        List<SemanticCatalog.SemanticMetric> metrics = SemanticMetricSearch.search(
            ctx.metrics(), args.keywords(), MAX_RESULTS);
        return new SemanticCatalog(metrics, ctx.relations());
    }
}
