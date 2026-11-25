package com.dati.mcp.domain.service;

import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.db.analysis.TableRef;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpServiceDataScope;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.param.SearchMetadataArgs;
import com.dati.datasource.domain.model.DataSourceDef;
import com.dati.datasource.domain.model.TableDef;
import com.dati.mcp.server.pojo.SearchHit;
import com.dati.mcp.server.pojo.ToolTestData;
import com.dati.semantic.domain.model.TermDef;
import com.dati.semantic.domain.service.SemanticSearchService;
import com.dati.semantic.domain.service.SemanticSearchService.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SearchMetadataExecutor implements ToolExecutor {

    private final SemanticSearchService semanticSearchService;
    private final TableInfoDAO tableInfoDAO;

    public SearchMetadataExecutor(SemanticSearchService semanticSearchService,
                                  TableInfoDAO tableInfoDAO) {
        this.semanticSearchService = semanticSearchService;
        this.tableInfoDAO = tableInfoDAO;
    }

    @Override
    public McpToolType getToolType() {
        return McpToolType.SEARCH_METADATA;
    }

    @Override
    public ToolTestData execute(ToolExecutionContext ctx) {
        SearchMetadataArgs args = ctx.args(SearchMetadataArgs.class);
        List<String> keywords = args.keywords();

        if (ctx.scopeItems().isEmpty())
            return new SearchHit(keywords, List.of(), List.of());

        List<String> subjectIds = ctx.scopeItems().stream()
                .filter(s -> s.getScopeType() == McpDataScopeType.SUBJECT)
                .map(McpServiceDataScope::getReferenceId)
                .distinct()
                .toList();
        List<String> tableIds = searchableTableIds(ctx.scopeItems());

        SearchResult result = semanticSearchService.search(
                keywords, List.of(), subjectIds, tableIds);

        List<DataSourceDef> dataSources = result.dataSources().stream()
                .map(g -> new SemanticSearchService.DataSourceGroup(g.dataSourceId(), g.dataSourceName(),
                        g.dbType(), g.defaultSchema(), g.description(),
                        g.tables().stream()
                                .filter(table -> McpScopeResolution.tableAllowed(ctx.scopeItems(),
                                        g.dataSourceId(), table.schema(), table.tableName(), g.defaultSchema()))
                                .toList()))
                .filter(g -> !g.tables().isEmpty())
                .map(g -> new DataSourceDef(g.dataSourceId(), g.dataSourceName(),
                        g.dbType(), g.defaultSchema(), g.description(),
                        g.tables().stream()
                                .map(tm -> new TableDef(tm.tableName(), tm.schema(),
                                        tm.description(), tm.aliases(), tm.columns()))
                                .toList()))
                .toList();

        List<TermDef> terms = result.terms().stream()
                .map(t -> new TermDef(t.name(), t.description(), t.subjectName()))
                .toList();

        return new SearchHit(keywords, dataSources, terms);
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
