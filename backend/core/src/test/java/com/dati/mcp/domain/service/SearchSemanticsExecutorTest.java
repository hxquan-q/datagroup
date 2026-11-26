package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.param.SearchSemanticsArgs;
import com.dati.mcp.server.pojo.SemanticCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import com.dati.mcp.domain.model.McpServiceSnapshot.TableRelationDraft;
import com.dati.datasource.domain.service.TableMetadataService;
import com.dati.datasource.repository.dao.ColumnInfoDAO;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.ColumnInfoPO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.semantic.domain.service.SemanticSearchService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchSemanticsExecutor tests")
class SearchSemanticsExecutorTest {

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private TableInfoDAO tableInfoDAO;

    @Mock
    private ColumnInfoDAO columnInfoDAO;

    private SearchSemanticsExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SearchSemanticsExecutor(semanticSearchService, tableInfoDAO, columnInfoDAO);
        lenient().when(semanticSearchService.search(anyList(), anyList(), anyList(), anyList()))
            .thenReturn(new SemanticSearchService.SearchResult(List.of(), List.of()));
    }

    private McpServiceSnapshot.MetricDraft metric() {
        return new McpServiceSnapshot.MetricDraft(
            "metric-1", "subject-1", "ds-1", "quality_daily_fpy", "质量日报 FPY",
            "Quality daily first pass yield", List.of("FPY", "质量合格率"), "owner",
            "day", "FDate", "Asia/Shanghai", List.of("workshop"),
            "SELECT 1", List.of(new com.dati.semantic.domain.model.MetricParameter("start", "String", true, null, null)), 30, 1000, 1);
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
        assertThat(result.metrics().getFirst().callExample().metric()).isEqualTo("metric-1");
        assertThat(result.metrics().getFirst().callExample().arguments()).containsKey("start");
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

    @Test
    @DisplayName("exposes published table relations for agent join discovery")
    void exposesPublishedRelations() {
        TableRelationDraft relation = new TableRelationDraft(
            "subject-1", "fact-id", "ds-1", "public", "orders", "customer_id",
            "dim-id", "ds-1", "public", "customers", "id", "MANY_TO_ONE", "owner", 2);
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.SEARCH_SEMANTICS, new ToolConfig.SearchSemanticsConfig(),
            new SearchSemanticsArgs(List.of()), List.of(), List.of(metric()), List.of(relation));

        SemanticCatalog result = (SemanticCatalog) executor.execute(ctx);

        assertThat(result.relations()).containsExactly(relation);
    }
    @Test
    @DisplayName("table aliases can discover their confirmed joins")
    void aliasesDiscoverRelations() {
        TableRelationDraft relation = new TableRelationDraft(
            "subject-1", "fact-id", "ds-1", "public", "orders", "customer_id",
            "dim-id", "ds-1", "public", "customers", "id", "MANY_TO_ONE", "owner", 2);
        TableInfoPO customers = new TableInfoPO();
        customers.setId("dim-id");
        customers.setDataSourceId("ds-1");
        customers.setSchema("public");
        customers.setName("customers");
        customers.setAliases(List.of("client"));
        when(tableInfoDAO.findAllById(anyCollection())).thenReturn(List.of(customers));
        ColumnInfoPO idColumn = new ColumnInfoPO();
        idColumn.setTableId("dim-id");
        idColumn.setName("id");
        when(columnInfoDAO.findByTableIdIn(anyCollection())).thenReturn(List.of(idColumn));
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.SEARCH_SEMANTICS, new ToolConfig.SearchSemanticsConfig(),
            new SearchSemanticsArgs(List.of("client")), List.of(), List.of(metric()), List.of(relation));

        SemanticCatalog result = (SemanticCatalog) executor.execute(ctx);

        assertThat(result.relations()).containsExactly(relation);
    }

    @Test
    @DisplayName("business term metadata hits can discover their confirmed joins")
    void semanticHitsDiscoverRelations() {
        TableRelationDraft relation = new TableRelationDraft(
            "subject-1", "fact-id", "ds-1", "public", "orders", "customer_id",
            "dim-id", "ds-1", "public", "customers", "id", "MANY_TO_ONE", "owner", 2);
        TableMetadataService.TableMeta customers = new TableMetadataService.TableMeta(
            "dim-id", "public", "customers", null, List.of(), "ds-1", List.of());
        when(semanticSearchService.search(anyList(), anyList(), anyList(), anyList()))
            .thenReturn(new SemanticSearchService.SearchResult(List.of(
                new SemanticSearchService.DataSourceGroup("ds-1", "Warehouse", "MYSQL", "public", null, List.of(customers))), List.of()));
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.SEARCH_SEMANTICS, new ToolConfig.SearchSemanticsConfig(),
            new SearchSemanticsArgs(List.of("buyer")), List.of(), List.of(metric()), List.of(relation));

        SemanticCatalog result = (SemanticCatalog) executor.execute(ctx);

        assertThat(result.relations()).containsExactly(relation);
    }

}
