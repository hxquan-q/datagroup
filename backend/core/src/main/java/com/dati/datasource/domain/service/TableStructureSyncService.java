package com.dati.datasource.domain.service;

import com.dati.base.RequestContext;
import com.dati.datasource.repository.dao.TableForeignKeyDAO;
import com.dati.datasource.repository.po.TableForeignKeyPO;
import com.dati.db.ForeignKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TableStructureSyncService {
    private final JdbcMetaService jdbcMetaService;
    private final TableForeignKeyDAO tableForeignKeyDAO;

    public TableStructureSyncService(JdbcMetaService jdbcMetaService, TableForeignKeyDAO tableForeignKeyDAO) {
        this.jdbcMetaService = jdbcMetaService;
        this.tableForeignKeyDAO = tableForeignKeyDAO;
    }

    /** Refreshes physical foreign keys while preserving the previous snapshot when metadata cannot be read. */
    public void syncForeignKeys(String datasourceId, String tableId, String schema, String tableName) {
        List<ForeignKey> keys;
        try {
            keys = jdbcMetaService.getImportedKeys(datasourceId, schema, tableName);
        } catch (SQLException | RuntimeException e) {
            log.warn("Foreign key metadata unavailable for {}.{}: {}", schema, tableName, e.getMessage());
            return;
        }
        tableForeignKeyDAO.deleteByTableId(tableId);
        if (keys.isEmpty()) return;
        String userId = RequestContext.getUser() != null ? RequestContext.getUser().getId() : null;
        List<TableForeignKeyPO> rows = new ArrayList<>();
        for (ForeignKey key : keys) {
            TableForeignKeyPO po = new TableForeignKeyPO();
            po.setDatasourceId(datasourceId);
            po.setTableId(tableId);
            po.setName(key.name() == null || key.name().isBlank() ? tableName + "_" + key.column() + "_fk" : key.name());
            po.setColumnName(key.column());
            po.setRefSchema(key.refSchema());
            po.setRefTable(key.refTable());
            po.setRefColumn(key.refColumn());
            po.setOnUpdate(key.onUpdate());
            po.setOnDelete(key.onDelete());
            po.setKeySeq(key.keySeq());
            po.setCreatedBy(userId);
            po.setUpdatedBy(userId);
            rows.add(po);
        }
        tableForeignKeyDAO.saveAll(rows);
    }
}
