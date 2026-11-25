package com.dati.mcp.domain.service;

import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpResolvedTable;
import com.dati.mcp.domain.model.McpServiceDataScope;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

/** Resolves the current table membership of draft scope items. */
@Component
public class McpScopeResolver {

    private final TableInfoDAO tableInfoDAO;
    private final SubjectTableDAO subjectTableDAO;

    public McpScopeResolver(TableInfoDAO tableInfoDAO, SubjectTableDAO subjectTableDAO) {
        this.tableInfoDAO = tableInfoDAO;
        this.subjectTableDAO = subjectTableDAO;
    }

    public List<McpServiceDataScope> resolve(List<McpServiceDataScope> scopeItems) {
        for (McpServiceDataScope scope : scopeItems) {
            if (!McpScopeResolution.usesFrozenTables(scope)) {
                scope.setResolvedTables(List.of());
                continue;
            }
            List<TableInfoPO> tables;
            if (scope.getScopeType() == McpDataScopeType.DATA_SOURCE) {
                tables = tableInfoDAO.findByDataSourceId(scope.getReferenceId());
            } else if (scope.getScopeType() == McpDataScopeType.SUBJECT) {
                tables = subjectTableDAO.findTablesBySubjectId(scope.getReferenceId(), Pageable.unpaged()).getContent();
            } else {
                tables = List.of();
            }
            scope.setResolvedTables(tables.stream()
                .map(t -> new McpResolvedTable(t.getDataSourceId(), t.getSchema(), t.getName()))
                .distinct()
                .toList());
        }
        return scopeItems;
    }
}
