package com.dati.db.client;

import com.dati.db.Column;
import com.dati.db.ForeignKey;
import com.dati.db.HikariPoolManager;
import com.dati.db.JdbcConnector;
import com.dati.db.Table;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractDbClient implements DbClient {

    private static final String CATALOG_COLUMN = "TABLE_CAT";
    private static final String SCHEMA_COLUMN = "TABLE_SCHEM";
    private static final String TABLE_COLUMN = "TABLE_NAME";

    /**
     * Indicates whether the database uses JDBC Catalog as the user-facing Schema concept.
     * MySQL-family databases (MySQL, MariaDB, Doris) return true;
     * standard schema databases (PostgreSQL, ClickHouse, Oracle) return false (default).
     */
    protected boolean isCatalogAsSchema() {
        return false;
    }

    @Override
    public List<String> getSchemas(JdbcConnector jdbcConnector) throws SQLException {
        return isCatalogAsSchema() ? getCatalogs(jdbcConnector) : getStandardSchemas(jdbcConnector);
    }

    @Override
    public List<Table> getTables(JdbcConnector jdbcConnector, String schema) throws SQLException {
        String actualCatalog = isCatalogAsSchema() ? schema : null;
        String actualSchema = isCatalogAsSchema() ? null : schema;

        List<Table> tables = new ArrayList<>();
        try (Connection connection = HikariPoolManager.getConnection(jdbcConnector);
             ResultSet resultSet = connection.getMetaData().getTables(actualCatalog, actualSchema, null, new String[]{"TABLE", "VIEW"})) {
            while (resultSet.next()) {
                String tableName = resultSet.getString(TABLE_COLUMN);
                String tableComment = resultSet.getString("REMARKS");
                tables.add(new Table(tableName, tableComment));
            }
        }
        return tables;
    }

    @Override
    public List<Column> getColumns(JdbcConnector jdbcConnector, String schema, String table) throws SQLException {
        try (Connection connection = HikariPoolManager.getConnection(jdbcConnector)) {
            return getColumns(connection, schema, table);
        }
    }

    @Override
    public List<Column> getColumns(Connection connection, String schema, String table) throws SQLException {
        String actualCatalog = isCatalogAsSchema() ? schema : null;
        String actualSchema = isCatalogAsSchema() ? null : schema;

        List<Column> columns = new ArrayList<>();
        try (ResultSet resultSet = connection.getMetaData().getColumns(actualCatalog, actualSchema, table, null)) {
            while (resultSet.next()) {
                columns.add(new Column(
                    resultSet.getString("COLUMN_NAME"),
                    resultSet.getString("TYPE_NAME"),
                    resultSet.getString("REMARKS")));
            }
        }
        Set<String> primaryKeys = primaryKeyColumns(connection, actualCatalog, actualSchema, table);
        Set<String> uniqueColumns = singleColumnUniqueIndexes(connection, actualCatalog, actualSchema, table);
        if (primaryKeys.size() == 1) uniqueColumns.addAll(primaryKeys);
        return columns.stream()
            .map(column -> new Column(column.name(), column.type(), column.comment(),
                primaryKeys.contains(column.name()), uniqueColumns.contains(column.name())))
            .toList();
    }

    @Override
    public List<ForeignKey> getImportedKeys(JdbcConnector jdbcConnector, String schema, String table) throws SQLException {
        try (Connection connection = HikariPoolManager.getConnection(jdbcConnector)) {
            return getImportedKeys(connection, schema, table);
        }
    }

    @Override
    public List<ForeignKey> getImportedKeys(Connection connection, String schema, String table) throws SQLException {
        String actualCatalog = isCatalogAsSchema() ? schema : null;
        String actualSchema = isCatalogAsSchema() ? null : schema;
        List<ForeignKey> keys = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getImportedKeys(actualCatalog, actualSchema, table)) {
            while (rs.next()) {
                keys.add(new ForeignKey(
                    value(rs.getString("FK_NAME")),
                    rs.getString("FKCOLUMN_NAME"),
                    referencedSchema(rs),
                    rs.getString("PKTABLE_NAME"),
                    rs.getString("PKCOLUMN_NAME"),
                    ruleName(rs.getShort("UPDATE_RULE")),
                    ruleName(rs.getShort("DELETE_RULE")),
                    rs.getShort("KEY_SEQ")));
            }
        }
        return keys;
    }

    private String referencedSchema(ResultSet rs) throws SQLException {
        return isCatalogAsSchema() ? value(rs.getString("PKTABLE_CAT")) : value(rs.getString("PKTABLE_SCHEM"));
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String ruleName(short rule) {
        return switch (rule) {
            case java.sql.DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case java.sql.DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case java.sql.DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case java.sql.DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            default -> "NO ACTION";
        };
    }

    private static Set<String> primaryKeyColumns(Connection connection, String catalog, String schema, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet rs = connection.getMetaData().getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) result.add(rs.getString("COLUMN_NAME"));
        }
        return result;
    }

    private static Set<String> singleColumnUniqueIndexes(Connection connection, String catalog, String schema, String table) throws SQLException {
        Map<String, Set<String>> columnsByIndex = new HashMap<>();
        try (ResultSet rs = connection.getMetaData().getIndexInfo(catalog, schema, table, true, false)) {
            while (rs.next()) {
                if (rs.getBoolean("NON_UNIQUE") || rs.getShort("TYPE") == java.sql.DatabaseMetaData.tableIndexStatistic) continue;
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) continue;
                columnsByIndex.computeIfAbsent(indexName, ignored -> new HashSet<>()).add(columnName);
            }
        }
        Set<String> result = new HashSet<>();
        for (Set<String> columns : columnsByIndex.values()) {
            if (columns.size() == 1) result.addAll(columns);
        }
        return result;
    }

    protected List<String> getCatalogs(JdbcConnector jdbcConnector) throws SQLException {
        List<String> catalogs = new ArrayList<>();
        try (Connection connection = HikariPoolManager.getConnection(jdbcConnector);
             ResultSet resultSet = connection.getMetaData().getCatalogs()) {
            while (resultSet.next()) {
                catalogs.add(resultSet.getString(CATALOG_COLUMN));
            }
        }
        return catalogs;
    }

    protected List<String> getStandardSchemas(JdbcConnector jdbcConnector) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (Connection connection = HikariPoolManager.getConnection(jdbcConnector);
             ResultSet resultSet = connection.getMetaData().getSchemas()) {
            while (resultSet.next()) {
                schemas.add(resultSet.getString(SCHEMA_COLUMN));
            }
        }
        return schemas;
    }

    /**
     * Helper to execute a query returning a single String result (e.g. SELECT DATABASE(), SELECT current_schema()).
     */
    protected String querySingleString(Connection connection, String sql) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        }
    }
}
