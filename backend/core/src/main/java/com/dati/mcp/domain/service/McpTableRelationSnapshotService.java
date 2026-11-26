package com.dati.mcp.domain.service;

import com.dati.datasource.repository.dao.ColumnInfoDAO;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.ColumnInfoPO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.mcp.domain.model.McpServiceDataScope;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.repository.mapper.McpServiceSnapshotMapper;
import com.dati.semantic.repository.dao.TableRelationDAO;
import com.dati.semantic.repository.po.TableRelationPO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class McpTableRelationSnapshotService {

    private final TableRelationDAO tableRelationDAO;
    private final TableInfoDAO tableInfoDAO;
    private final ColumnInfoDAO columnInfoDAO;

    public McpTableRelationSnapshotService(TableRelationDAO tableRelationDAO,
                                           TableInfoDAO tableInfoDAO,
                                           ColumnInfoDAO columnInfoDAO) {
        this.tableRelationDAO = tableRelationDAO;
        this.tableInfoDAO = tableInfoDAO;
        this.columnInfoDAO = columnInfoDAO;
    }

    public List<McpServiceSnapshot.TableRelationDraft> buildRelationDrafts(List<String> subjectIds,
                                                                            List<McpServiceDataScope> scopeItems) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        List<TableRelationPO> rows = new ArrayList<>();
        for (String subjectId : subjectIds) {
            List<TableRelationPO> subjectRows = tableRelationDAO.findBySubjectId(subjectId);
            if (subjectRows != null) {
                rows.addAll(subjectRows);
            }
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> tableIds = rows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.getFactTableId(), row.getDimensionTableId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, TableInfoPO> tablesById = tableInfoDAO.findAllById(tableIds).stream()
                .collect(Collectors.toMap(TableInfoPO::getId, Function.identity(), (a, b) -> a));
        Set<String> existingColumns = columnInfoDAO.findByTableIdIn(tableIds).stream()
                .map(column -> columnKey(column.getTableId(), column.getName()))
                .collect(Collectors.toSet());
        return rows.stream()
                .filter(row -> tablesById.containsKey(row.getFactTableId()) && tablesById.containsKey(row.getDimensionTableId()))
                .filter(row -> existingColumns.contains(columnKey(row.getFactTableId(), row.getFactColumn()))
                        && existingColumns.contains(columnKey(row.getDimensionTableId(), row.getDimensionColumn())))
                .map(row -> McpServiceSnapshotMapper.toTableRelationDraft(row,
                        tablesById.get(row.getFactTableId()), tablesById.get(row.getDimensionTableId())))
                .filter(relation -> McpScopeResolution.relationAllowed(scopeItems, relation))
                .sorted((a, b) -> {
                    int byFact = a.factTableName().compareTo(b.factTableName());
                    if (byFact != 0) return byFact;
                    int byDimension = a.dimensionTableName().compareTo(b.dimensionTableName());
                    if (byDimension != 0) return byDimension;
                    return a.factColumn().compareTo(b.factColumn());
                })
                .toList();
    }

    private String columnKey(String tableId, String columnName) {
        return tableId + "\u0000" + columnName;
    }
}
