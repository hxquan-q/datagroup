package com.dati.mcp.domain.model;

/**
 * A table captured in a published service scope.
 */
public record McpResolvedTable(String dataSourceId, String schema, String tableName) {
}
