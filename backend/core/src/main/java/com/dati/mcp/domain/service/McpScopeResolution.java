package com.dati.mcp.domain.service;

import com.dati.db.analysis.TableRef;
import com.dati.mcp.domain.model.McpDataScopeAccessMode;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpResolvedTable;
import com.dati.mcp.domain.model.McpServiceDataScope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves effective data sources and frozen tables from published scope items. */
final class McpScopeResolution {

    private McpScopeResolution() {
    }

    static Set<String> resolvedDataSourceIds(List<McpServiceDataScope> scopeItems) {
        Set<String> result = new LinkedHashSet<>();
        if (scopeItems == null) {
            return result;
        }
        for (McpServiceDataScope scope : scopeItems) {
            if (scope.getScopeType() == McpDataScopeType.DATA_SOURCE) {
                result.add(scope.getReferenceId());
            } else if (scope.getScopeType() == McpDataScopeType.SUBJECT) {
                for (McpResolvedTable table : scope.getResolvedTables()) {
                    result.add(table.dataSourceId());
                }
            }
        }
        return result;
    }

    static Set<TableRef> frozenTableRefs(List<McpServiceDataScope> scopeItems, String dataSourceId) {
        Set<TableRef> result = new LinkedHashSet<>();
        if (scopeItems == null) {
            return result;
        }
        for (McpServiceDataScope scope : scopeItems) {
            if (!usesFrozenTables(scope)) {
                continue;
            }
            for (McpResolvedTable table : scope.getResolvedTables()) {
                if (dataSourceId.equals(table.dataSourceId())) {
                    result.add(new TableRef(table.schema(), table.tableName()));
                }
            }
        }
        return result;
    }

    static boolean usesFrozenTables(McpServiceDataScope scope) {
        if (scope.getScopeType() == McpDataScopeType.SUBJECT) {
            return true;
        }
        return scope.getScopeType() == McpDataScopeType.DATA_SOURCE
            && scope.getAccessMode() != McpDataScopeAccessMode.SCHEMA_SCOPE;
    }

    static boolean tableAllowed(List<McpServiceDataScope> scopeItems, String dataSourceId,
                                String schema, String tableName, String defaultSchema) {
        Set<String> schemas = McpDataScopeSchemas.allowedSchemas(scopeItems, dataSourceId);
        if (schemas.contains(schema) || (schema == null && schemas.contains(defaultSchema))) {
            return true;
        }
        Set<TableRef> frozen = frozenTableRefs(scopeItems, dataSourceId);
        return frozen.contains(new TableRef(schema, tableName))
            || (schema == null && frozen.contains(new TableRef(defaultSchema, tableName)));
    }

}
