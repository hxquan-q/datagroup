package com.dati.mcp.domain.service;

import com.dati.datasource.domain.model.DataSourceDef;
import com.dati.datasource.domain.model.TableDef;
import com.dati.datasource.repository.dao.ColumnInfoDAO;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.ColumnInfoPO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.param.SearchSemanticsArgs;
import com.dati.mcp.server.pojo.SemanticCatalog;
import com.dati.mcp.server.pojo.ToolTestData;
import com.dati.semantic.domain.service.SemanticSearchService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SearchSemanticsExecutor implements ToolExecutor {

    private static final int MAX_RESULTS = 20;

    private final SemanticSearchService semanticSearchService;
    private final TableInfoDAO tableInfoDAO;
    private final ColumnInfoDAO columnInfoDAO;

    public SearchSemanticsExecutor(SemanticSearchService semanticSearchService,
                                   TableInfoDAO tableInfoDAO, ColumnInfoDAO columnInfoDAO) {
        this.semanticSearchService = semanticSearchService;
        this.tableInfoDAO = tableInfoDAO;
        this.columnInfoDAO = columnInfoDAO;
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

    private List<DataSourceDef> searchMatchedTables(ToolExecutionContext ctx, List<String> rawKeywords) {
        List<String> keywords = rawKeywords == null ? List.of() : rawKeywords.stream()
            .map(keyword -> keyword == null ? "" : keyword.toLowerCase(Locale.ROOT).trim())
            .filter(keyword -> !keyword.isEmpty())
            .toList();
        if (keywords.isEmpty() || ctx.relations().isEmpty()) {
            return List.of();
        }
        Set<String> endpointTableIds = ctx.relations().stream()
            .flatMap(relation -> java.util.stream.Stream.of(relation.factTableId(), relation.dimensionTableId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TableInfoPO> tables = tableInfoDAO.findAllById(endpointTableIds);
        Map<String, List<ColumnInfoPO>> columnsByTable = columnInfoDAO.findByTableIdIn(endpointTableIds).stream()
            .collect(Collectors.groupingBy(ColumnInfoPO::getTableId));

        Map<String, Map<String, TableDef>> tablesByDataSource = new LinkedHashMap<>();
        for (TableInfoPO table : tables) {
            if (matchesAny(table, columnsByTable.getOrDefault(table.getId(), List.of()), keywords)) {
                addTable(tablesByDataSource, table.getDataSourceId(), new TableDef(table.getName(), table.getSchema(),
                    table.getDescription(), table.getAliases(), List.of()));
            }
        }

        List<String> subjectIds = ctx.relations().stream()
            .map(McpServiceSnapshot.TableRelationDraft::subjectId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        mergeSemanticTables(tablesByDataSource,
            semanticSearchService.search(rawKeywords, List.of(), subjectIds, List.of(), 100), endpointTableIds);
        int endpointSearchLimit = Math.min(1000, Math.max(200, endpointTableIds.size() * 20));
        mergeSemanticTables(tablesByDataSource,
            semanticSearchService.search(rawKeywords, List.of(), List.of(), List.copyOf(endpointTableIds), endpointSearchLimit),
            endpointTableIds);
        return tablesByDataSource.entrySet().stream()
            .map(entry -> new DataSourceDef(entry.getKey(), entry.getKey(), null, null, null,
                List.copyOf(entry.getValue().values())))
            .toList();
    }

    private void mergeSemanticTables(Map<String, Map<String, TableDef>> tablesByDataSource,
                                     SemanticSearchService.SearchResult result, Set<String> endpointTableIds) {
        for (SemanticSearchService.DataSourceGroup group : result.dataSources()) {
            for (var table : group.tables()) {
                if (!endpointTableIds.contains(table.tableId())) {
                    continue;
                }
                addTable(tablesByDataSource, group.dataSourceId(), new TableDef(table.tableName(), table.schema(),
                    table.description(), table.aliases(), table.columns()));
            }
        }
    }

    private void addTable(Map<String, Map<String, TableDef>> tablesByDataSource, String dataSourceId, TableDef table) {
        tablesByDataSource.computeIfAbsent(dataSourceId, key -> new LinkedHashMap<>())
            .putIfAbsent((table.schema() == null ? "" : table.schema()) + "|" + table.table(), table);
    }

    private boolean matchesAny(TableInfoPO table, List<ColumnInfoPO> columns, List<String> keywords) {
        return keywords.stream().anyMatch(keyword ->
            contains(table.getName(), keyword) || contains(table.getDescription(), keyword)
                || containsAny(table.getAliases(), keyword)
                || columns.stream().anyMatch(column -> contains(column.getName(), keyword)
                    || contains(column.getDescription(), keyword) || containsAny(column.getAliases(), keyword)));
    }

    private boolean containsAny(List<String> values, String keyword) {
        return values != null && values.stream().anyMatch(value -> contains(value, keyword));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
