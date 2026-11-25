package com.dati.mcp.server.converter;

import com.dati.datasource.domain.model.ColumnDef;
import com.dati.datasource.domain.model.TableDef;
import com.dati.mcp.domain.model.ToolError;
import com.dati.mcp.domain.service.ToolExecuteException;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.StatementResult;
import com.dati.mcp.server.pojo.TableMetadata;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolResultConverter tests")
class ToolResultConverterTest {

    private final ToolResultConverter converter = new ToolResultConverter();

    @Test
    @DisplayName("sql execution becomes text content plus structuredContent")
    void convertsSqlExecution() {
        SqlExecution execution = new SqlExecution("SELECT 1",
            List.of(StatementResult.select(List.of("a"), List.of(List.of(1)), false)), null);
        McpSchema.CallToolResult result = converter.toResult(execution);
        assertFalse(result.isError());
        assertEquals(1, result.content().size());
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().getFirst();
        assertNotNull(text.text());
        assertNotNull(result.structuredContent());
    }

    @Test
    @DisplayName("table metadata becomes structured content")
    void convertsTableMetadata() {
        TableDef table = new TableDef("public", "users", "user table", List.of(),
            List.of(new ColumnDef("id", "bigint", "primary key", List.of(), List.of("1", "2"))));
        TableMetadata metadata = new TableMetadata(List.of(table));
        McpSchema.CallToolResult result = converter.toResult(metadata);
        assertFalse(result.isError());
        assertNotNull(result.structuredContent());
    }

    @Test
    @DisplayName("search hit becomes structured content")
    void convertsSearchHit() {
        com.dati.mcp.server.pojo.SearchHit hit = new com.dati.mcp.server.pojo.SearchHit(
            List.of("orders"), List.of(), List.of());
        McpSchema.CallToolResult result = converter.toResult(hit);
        assertFalse(result.isError());
        assertNotNull(result.structuredContent());
    }

    @Test
    @DisplayName("tool execute exception becomes isError result with text message")
    void convertsException() {
        ToolExecuteException e = new ToolExecuteException(ToolError.SCOPE_VIOLATION, "table not in scope");
        McpSchema.CallToolResult result = converter.toError(e);
        assertTrue(result.isError());
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().getFirst();
        assertTrue(text.text().contains("table not in scope"));
    }

    @Test
    @DisplayName("metadata update results serialize with snake_case and old/new keys")
    void convertsMetadataUpdate() {
        com.dati.mcp.server.pojo.MetadataUpdateData data =
            new com.dati.mcp.server.pojo.MetadataUpdateData(List.of(
                new com.dati.mcp.server.pojo.MetadataUpdateResult(
                    "TABLE", "sales", true, "UPDATE",
                    Map.of("description", "old desc", "aliases", List.of("a")),
                    Map.of("description", "new desc", "aliases", List.of("a", "b")),
                    null),
                new com.dati.mcp.server.pojo.MetadataUpdateResult(
                    "TERM", "退货单", false, null, null, null,
                    new com.dati.mcp.server.pojo.MetadataUpdateResult.MetadataUpdateError(
                        "SCOPE_ERROR", "Subject 财务 not in service scope"))));

        McpSchema.CallToolResult result = converter.toResult(data);
        assertFalse(result.isError());
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("\"type\":\"METADATA_UPDATE\""));
        assertTrue(text.contains("\"entity_type\":\"TABLE\""));
        assertTrue(text.contains("\"change_type\":\"UPDATE\""));
        assertTrue(text.contains("\"new\":{"));
        assertTrue(text.contains("\"description\":\"new desc\""));
        assertTrue(text.contains("\"error_category\":\"SCOPE_ERROR\""));
        // Check that null fields are omitted
        assertFalse(text.contains("\"error\":null"));
        assertFalse(text.contains("\"old\":null"));
        assertFalse(text.contains("\"change_type\":null"));
        assertNotNull(result.structuredContent());
    }

    @Test
    @DisplayName("semantic catalog and metric execution serialize with protocol type markers")
    void convertsSemanticResults() {
        com.dati.mcp.server.pojo.SemanticCatalog catalog = new com.dati.mcp.server.pojo.SemanticCatalog(List.of(
            new com.dati.mcp.server.pojo.SemanticCatalog.SemanticMetric(
                "metric-1", "subject-1", "quality_daily_fpy", "Quality", null,
                List.of(), null, null, null, null, List.of(), List.of(), 1)));
        McpSchema.CallToolResult catalogResult = converter.toResult(catalog);
        String catalogText = ((McpSchema.TextContent) catalogResult.content().getFirst()).text();
        assertTrue(catalogText.contains("\"type\":\"SEMANTIC_CATALOG\""));
        assertTrue(catalogText.contains("quality_daily_fpy"));

        com.dati.mcp.server.pojo.SqlExecution execution = new com.dati.mcp.server.pojo.SqlExecution(
            "SELECT 1", List.of(), List.of());
        com.dati.mcp.server.pojo.MetricExecution metricExecution = new com.dati.mcp.server.pojo.MetricExecution(
            new com.dati.mcp.server.pojo.MetricExecution.MetricInfo(
                "metric-1", "subject-1", "quality_daily_fpy", "Quality", null,
                null, null, null, List.of(), 1),
            Map.of(), List.of(), execution);
        McpSchema.CallToolResult metricResult = converter.toResult(metricExecution);
        String metricText = ((McpSchema.TextContent) metricResult.content().getFirst()).text();
        assertTrue(metricText.contains("\"type\":\"METRIC_EXECUTION\""));
        assertTrue(metricText.contains("\"execution\""));
    }
}
