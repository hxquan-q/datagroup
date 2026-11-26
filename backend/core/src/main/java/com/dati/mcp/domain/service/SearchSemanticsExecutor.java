package com.dati.mcp.domain.service;

import com.dati.datasource.domain.model.DataSourceDef;
import com.dati.datasource.domain.model.TableDef;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.db.analysis.TableRef;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpServiceDataScope;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.param.SearchSemanticsArgs;
import com.dati.mcp.server.pojo.SemanticCatalog;
import com.dati.mcp.server.pojo.ToolTestData;
import com.dati.semantic.domain.service.SemanticSearchService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SearchSemanticsExecutor implements ToolExecutor {

    private static final int MAX_RESULTS = 20;

    private final SemanticSearchService semanticSearchService;
    private final TableInfoDAO tableInfoDAO;

    public SearchSemanticsExecutor(SemanticSearchService semanticSearchService, TableInfoDAO tableInfoDAO) {
        this.semanticSearchService = semanticSearchService;
        this.tableInfoDAO = tableInfoDAO;
    }

    @Override
    public McpToolType getToolType() {
        return McpToolType.SEARCH_SEMANTICS;
    }

    @Override
    public ToolTestData execute(ToolExecutionContext ctx) {
        SearchSemanticsArgs args = ctx.args(SearchSemanticsArgs.class);
        List<SemanticCatalog.SemanticMetric> metrics = SemanticMetricSearch.search(
            ctx.metrics(), args.keywords(), MAX_RESULTS);
        List<DataSourceDef> matchedTables = searchMatchedTables(ctx, args.keywords());
        List<McpServiceSnapshot.TableRelationDraft> relations = ctx.relations().stream()
            .filter(relation -> TableRelationSearch.relevant(relation, args.keywords(), matchedTables))
            .toList();
        return new SemanticCatalog(metrics, relations);
    }

    private List<DataSourceDef> searchMatchedTables(ToolExecutionContext ctx, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        List<String> subjectIds = ctx.scopeItems().stream()
            .filter(scope -> scope.getScopeType() == McpDataScopeType.SUBJECT)
            .map(McpServiceDataScope::getReferenceId)
            .distinct()
            .toList();
        SemanticSearchService.SearchResult result = semanticSearchService.search(
            keywords, List.of(), subjectIds, searchableTableIds(ctx.scopeItems()));
        return result.dataSources().stream()
            .map(group -> new DataSourceDef(group.dataSourceId(), group.dataSourceName(), group.dbType(),
                group.defaultSchema(), group.description(), group.tables().stream()
                    .map(table -> new TableDef(table.tableName(), table.schema(), table.description(),
                        table.aliases(), table.columns()))
                    .toList()))
            .toList();
    }

    private List<String> searchableTableIds(List<McpServiceDataScope> scopeItems) {
        Set<String> tableIds = new java.util.LinkedHashSet<>();
        for (String dsId : McpScopeResolution.resolvedDataSourceIds(scopeItems)) {
            List<TableInfoPO> importedTables = tableInfoDAO.findByDataSourceId(dsId);
            if (importedTables == null || importedTables.isEmpty()) {
                continue;
            }
            Set<String> allowedSchemas = McpDataScopeSchemas.allowedSchemas(scopeItems, dsId);
            Set<TableRef> frozenTables = McpScopeResolution.frozenTableRefs(scopeItems, dsId);
            for (TableInfoPO table : importedTables) {
                boolean includedBySchema = allowedSchemas.contains(table.getSchema());
                boolean includedByFrozenScope = frozenTables.contains(new TableRef(table.getSchema(), table.getName()));
                if ((includedBySchema || includedByFrozenScope) && table.getId() != null) {
                    tableIds.add(table.getId());
                }
            }
        }
        return List.copyOf(tableIds);
    }
}
