package com.dati.mcp.domain.service;

import com.dati.datasource.domain.model.DataSourceDef;
import com.dati.mcp.domain.model.McpServiceSnapshot;

import java.util.List;
import java.util.Locale;

final class TableRelationSearch {

    private TableRelationSearch() {
    }

    static boolean relevant(McpServiceSnapshot.TableRelationDraft relation, List<String> rawKeywords,
                            List<DataSourceDef> dataSources) {
        List<String> keywords = rawKeywords == null ? List.of() : rawKeywords.stream()
            .map(keyword -> keyword == null ? "" : keyword.toLowerCase(Locale.ROOT).trim())
            .filter(keyword -> !keyword.isEmpty())
            .toList();
        if (keywords.isEmpty()) {
            return true;
        }
        if (keywords.stream().anyMatch(keyword -> matches(relation, keyword))) {
            return true;
        }
        return dataSources != null && dataSources.stream()
            .flatMap(group -> group.tables().stream().map(table -> new Endpoint(group.id(), table.schema(), table.table())))
            .anyMatch(endpoint -> touches(relation, endpoint));
    }

    private static boolean matches(McpServiceSnapshot.TableRelationDraft relation, String keyword) {
        return contains(relation.factTableName(), keyword) || contains(relation.factColumn(), keyword)
            || contains(relation.dimensionTableName(), keyword) || contains(relation.dimensionColumn(), keyword);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static boolean touches(McpServiceSnapshot.TableRelationDraft relation, Endpoint endpoint) {
        return endpointMatches(endpoint, relation.factDataSourceId(), relation.factSchema(), relation.factTableName())
            || endpointMatches(endpoint, relation.dimensionDataSourceId(), relation.dimensionSchema(), relation.dimensionTableName());
    }

    private static boolean endpointMatches(Endpoint endpoint, String dataSourceId, String schema, String tableName) {
        return java.util.Objects.equals(endpoint.dataSourceId(), dataSourceId)
            && java.util.Objects.equals(endpoint.schema(), schema)
            && java.util.Objects.equals(endpoint.tableName(), tableName);
    }

    private record Endpoint(String dataSourceId, String schema, String tableName) {
    }
}
